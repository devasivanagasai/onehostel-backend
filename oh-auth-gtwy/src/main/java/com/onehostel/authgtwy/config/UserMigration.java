package com.onehostel.config;

import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.onehostel.constants.CollectionNames;
import com.onehostel.constants.CommonEnumHelper.UserStatus;
import com.onehostel.repository.UserRepository;
import com.onehostel.service.search.SearchMirrorService;
import com.onehostel.service.search.UserSearchMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Brings stored users up to the current model, before anything reads them (an old role name
 * would not map to the Role enum), then rebuilds their search-mirror rows, since these bulk
 * updates raise no save events. Every step is a no-op once done.
 * Disable with app.bootstrap.user-migration=false.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class UserMigration implements ApplicationRunner {

    private static final Map<String, String> ROLE_RENAMES = Map.of("ADMIN", "SUPER_ADMIN", "TENANT", "HOSTEL_TENANT");
    private static final Map<String, String> FIELD_RENAMES = Map.of("hostelBranchId", "hostelId", "unitId", "bedId");

    private final AppProperties properties;
    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final SearchMirrorService searchMirrorService;

    @Override
    public void run(ApplicationArguments args) {
        if (properties.bootstrap() == null || !properties.bootstrap().userMigration()) {
            return;
        }
        ROLE_RENAMES.forEach((from, to) -> report("role " + from + " -> " + to,
                update(Criteria.where("role").is(from), Update.update("role", to))));

        // The boolean "active" flag became "status".
        report("active=false -> INACTIVE", update(Criteria.where("status").exists(false).and("active").is(false),
                Update.update("status", UserStatus.INACTIVE.name())));
        report("status -> ACTIVE", update(Criteria.where("status").exists(false),
                Update.update("status", UserStatus.ACTIVE.name())));
        report("removed active flag", update(Criteria.where("active").exists(true), new Update().unset("active")));

        FIELD_RENAMES.forEach((from, to) -> report("field " + from + " -> " + to,
                update(Criteria.where(from).exists(true), new Update().rename(from, to))));

        userRepository.findAll().forEach(user -> searchMirrorService.upsert(UserSearchMapper.toSearchObject(user)));
    }

    private long update(Criteria criteria, Update update) {
        return mongoTemplate.updateMulti(new Query(criteria), update, CollectionNames.USERS).getModifiedCount();
    }

    private static void report(String step, long changed) {
        if (changed > 0) {
            log.info("User migration: {} on {} users", step, changed);
        }
    }
}
