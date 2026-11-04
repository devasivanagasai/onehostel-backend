package com.onehostel.scheduler.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import com.github.f4b6a3.ulid.UlidCreator;
import com.mongodb.client.result.UpdateResult;
import com.onehostel.scheduler.constants.SchedulerEnums.RunState;
import com.onehostel.scheduler.model.JobConfig;
import com.onehostel.scheduler.model.JobFailure;
import com.onehostel.scheduler.model.JobLock;
import com.onehostel.scheduler.model.JobRun;
import com.onehostel.scheduler.service.HostelJob;

/** All scheduler-core persistence: config, lease lock and cursor, run history, failure records. */
public class JobStore {

    private static final int MAX_ERROR_LENGTH = 500;

    private final MongoOperations mongo;

    public JobStore(MongoOperations mongo) {
        this.mongo = mongo;
    }

    /** Creates the job's config row from its defaults if there is none; an existing row (edited at runtime) wins. */
    public void seedConfig(HostelJob job) {
        Update defaults = new Update()
                .setOnInsert("cron", job.defaultCron())
                .setOnInsert("zone", job.defaultZone())
                .setOnInsert("enabled", true)
                .setOnInsert("misfirePolicy", job.defaultMisfirePolicy())
                .setOnInsert("lockTtlSeconds", job.defaultLockTtl().toSeconds())
                .setOnInsert("maxConsecutiveFailures", 10)
                .setOnInsert("maxFailureRate", 0.5)
                .setOnInsert("alertEmails", List.of());
        mongo.upsert(Query.query(Criteria.where("_id").is(job.name())), defaults, JobConfig.class);
    }

    public Optional<JobConfig> config(String jobName) {
        return Optional.ofNullable(mongo.findById(jobName, JobConfig.class));
    }

    public Optional<JobLock> lock(String jobName) {
        return Optional.ofNullable(mongo.findById(jobName, JobLock.class));
    }

    /**
     * Takes the lease for a tick: only when the lock is free (or lapsed) and the tick is newer than the cursor.
     * Atomic, so exactly one replica wins each tick.
     */
    public boolean tryClaim(String jobName, Instant tick, String holder, Duration ttl) {
        Instant now = Instant.now();
        Query claimable = Query.query(Criteria.where("_id").is(jobName).andOperator(
                new Criteria().orOperator(Criteria.where("lockedUntil").lt(now), Criteria.where("lockedUntil").exists(false)),
                new Criteria().orOperator(Criteria.where("lastTick").lt(tick), Criteria.where("lastTick").exists(false))));
        Update claim = new Update().set("holder", holder).set("lockedUntil", now.plus(ttl)).set("lastTick", tick);
        try {
            return mongo.findAndModify(claimable, claim, FindAndModifyOptions.options().upsert(true).returnNew(true),
                    JobLock.class) != null;
        } catch (DuplicateKeyException e) {
            // The lock row exists but did not match: held, or the tick is already claimed
            return false;
        }
    }

    /** Moves the cursor past a tick without running it. True for the one replica that moved it. */
    public boolean advanceCursor(String jobName, Instant tick) {
        Query older = Query.query(Criteria.where("_id").is(jobName).orOperator(
                Criteria.where("lastTick").lt(tick), Criteria.where("lastTick").exists(false)));
        try {
            UpdateResult result = mongo.upsert(older, new Update().set("lastTick", tick), JobLock.class);
            return result.getModifiedCount() > 0 || result.getUpsertedId() != null;
        } catch (DuplicateKeyException e) {
            // The row exists with a cursor at or past this tick: another replica moved it
            return false;
        }
    }

    /** Releases the lease; only the holder can. */
    public void release(String jobName, String holder) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(jobName).and("holder").is(holder)),
                new Update().set("lockedUntil", Instant.now()), JobLock.class);
    }

    public JobRun startRun(String jobName, Instant tick, String holder, Duration ttl) {
        JobRun run = new JobRun();
        run.setId(UlidCreator.getMonotonicUlid().toString());
        run.setJobName(jobName);
        run.setTick(tick);
        run.setState(RunState.RUNNING);
        run.setHolder(holder);
        run.setStartedAt(Instant.now());
        run.setExpiresAt(run.getStartedAt().plus(ttl));
        return mongo.insert(run);
    }

    public void finishRun(JobRun run) {
        run.setFinishedAt(Instant.now());
        mongo.save(run);
    }

    public void recordSkipped(String jobName, Instant tick, RunState state, String message) {
        JobRun run = new JobRun();
        run.setId(jobName + ":" + tick + ":" + state);
        run.setJobName(jobName);
        run.setTick(tick);
        run.setState(state);
        run.setStartedAt(Instant.now());
        run.setFinishedAt(run.getStartedAt());
        run.setMessage(message);
        mongo.save(run);
    }

    /** Heartbeat: RUNNING runs whose lease lapsed are ABANDONED. Returns them for alerting. */
    public List<JobRun> markAbandoned() {
        Instant now = Instant.now();
        Query lapsed = Query.query(Criteria.where("state").is(RunState.RUNNING).and("expiresAt").lt(now));
        List<JobRun> runs = mongo.find(lapsed, JobRun.class);
        for (JobRun run : runs) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(run.getId()).and("state").is(RunState.RUNNING)),
                    new Update().set("state", RunState.ABANDONED).set("finishedAt", now)
                            .set("message", "Lock lapsed while running: replica died or run overran its TTL"),
                    JobRun.class);
        }
        return runs;
    }

    public void recordFailure(String jobName, String itemType, String itemId, Exception error, String runId) {
        Instant now = Instant.now();
        String message = error.getClass().getSimpleName() + ": " + error.getMessage();
        Update update = new Update()
                .inc("attemptCount", 1)
                .set("lastError", message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message)
                .set("lastRunId", runId)
                .set("lastFailedAt", now)
                .set("resolved", false)
                .unset("resolvedAt")
                .setOnInsert("jobName", jobName)
                .setOnInsert("itemType", itemType)
                .setOnInsert("itemId", itemId)
                .setOnInsert("firstFailedAt", now);
        mongo.upsert(Query.query(Criteria.where("_id").is(JobFailure.idOf(jobName, itemType, itemId))), update, JobFailure.class);
    }

    public void resolveFailure(String jobName, String itemType, String itemId) {
        mongo.updateFirst(Query.query(Criteria.where("_id").is(JobFailure.idOf(jobName, itemType, itemId)).and("resolved").is(false)),
                new Update().set("resolved", true).set("resolvedAt", Instant.now()), JobFailure.class);
    }

    public List<String> openFailureIds(String jobName, String itemType) {
        Query open = Query.query(Criteria.where("jobName").is(jobName).and("itemType").is(itemType).and("resolved").is(false));
        open.fields().include("itemId");
        return mongo.find(open, JobFailure.class).stream().map(JobFailure::getItemId).toList();
    }
}
