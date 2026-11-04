package com.onehostel.tenancy.mongo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoNamespace;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.onehostel.tenancy.config.TenancyProperties;
import com.onehostel.tenancy.constants.TenancyEnums.TenancyMode;
import com.onehostel.tenancy.exception.TenantBoundaryViolationException;
import com.onehostel.tenancy.helper.TenantContext;

class TenantScopedCollectionTest {

    private MongoCollection<Document> raw;
    private ViolationSink sink;
    private MongoCollection<Document> rooms;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        raw = mock(MongoCollection.class);
        when(raw.getNamespace()).thenReturn(new MongoNamespace("onehostel_property", "rooms"));
        sink = mock(ViolationSink.class);
        rooms = scoped(TenancyMode.ENFORCE);
    }

    private MongoCollection<Document> scoped(TenancyMode mode) {
        TenantCollectionRegistry registry = mock(TenantCollectionRegistry.class);
        when(registry.isTenantCollection("rooms")).thenReturn(true);
        TenantBoundary boundary = new TenantBoundary(new TenancyProperties(mode, Map.of()), registry, sink);
        return TenantScopedCollection.wrap(raw, boundary);
    }

    @Test
    void readsAreLimitedToTheTenantAndItsAncestors() {
        TenantContext.runAs("branch-1", List.of("hostel-1"), () -> rooms.find(Filters.eq("roomNo", "101")));

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(raw).find(filter.capture());
        assertThat(render(filter.getValue())).isEqualTo(render(Filters.and(Filters.eq("roomNo", "101"),
                new Document("organizationId", new Document("$in", List.of("branch-1", "hostel-1"))))));
    }

    @Test
    void findWithoutFilterGetsTheTenantFilter() {
        TenantContext.runAs("branch-1", List.of(), () -> rooms.find());

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(raw).find(filter.capture());
        assertThat(render(filter.getValue()))
                .isEqualTo(render(new Document("organizationId", new Document("$in", List.of("branch-1")))));
    }

    @Test
    void writesAreLimitedToTheTenantItself() {
        Bson update = new Document("$set", new Document("floor", 2));
        TenantContext.runAs("branch-1", List.of("hostel-1"), () -> rooms.updateOne(Filters.eq("_id", "r1"), update));

        ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
        verify(raw).updateOne(filter.capture(), any(Bson.class));
        assertThat(render(filter.getValue()))
                .isEqualTo(render(Filters.and(Filters.eq("_id", "r1"), new Document("organizationId", "branch-1"))));
    }

    @Test
    void enforceFailsClosedWithoutATenant() {
        assertThatThrownBy(() -> rooms.countDocuments())
                .isInstanceOf(TenantBoundaryViolationException.class)
                .hasMessageContaining("rooms");
        verifyNoInteractions(sink);
    }

    @Test
    void unscopedContextPassesThrough() {
        TenantContext.runUnscoped("migration", () -> rooms.deleteMany(Filters.eq("roomNo", "9")));

        verify(raw).deleteMany(Filters.eq("roomNo", "9"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aggregationStartsWithATenantMatch() {
        Bson group = new Document("$group", new Document("_id", "$floor"));
        TenantContext.runAs("branch-1", List.of(), () -> rooms.aggregate(List.of(group)));

        ArgumentCaptor<List<Bson>> pipeline = ArgumentCaptor.forClass(List.class);
        verify(raw).aggregate(pipeline.capture());
        assertThat(pipeline.getValue()).hasSize(2);
        assertThat(render(pipeline.getValue().get(0)))
                .isEqualTo(render(new Document("$match", new Document("organizationId", new Document("$in", List.of("branch-1"))))));
    }

    @Test
    void insertIsStampedAndForeignInsertRefused() {
        Document mine = new Document("roomNo", "101");
        TenantContext.runAs("branch-1", List.of(), () -> rooms.insertOne(mine));
        assertThat(mine.getString("organizationId")).isEqualTo("branch-1");

        Document foreign = new Document("roomNo", "102").append("organizationId", "branch-2");
        assertThatThrownBy(() -> TenantContext.runAs("branch-1", List.of(), () -> rooms.insertOne(foreign)))
                .isInstanceOf(TenantBoundaryViolationException.class);
    }

    @Test
    void bulkWriteIsRefusedInEnforce() {
        assertThatThrownBy(() -> TenantContext.runAs("branch-1", List.of(), () -> rooms.bulkWrite(List.of())))
                .isInstanceOf(TenantBoundaryViolationException.class);
    }

    @Test
    void shadowRecordsButDoesNotChangeTheQuery() {
        MongoCollection<Document> shadowRooms = scoped(TenancyMode.SHADOW);
        shadowRooms.find(Filters.eq("roomNo", "101"));

        verify(raw).find(Filters.eq("roomNo", "101"));
        verify(sink).record("rooms", "find", null, "no tenant in context");
    }

    @Test
    void globalCollectionsAreNotScoped() {
        TenantCollectionRegistry registry = mock(TenantCollectionRegistry.class);
        TenantBoundary boundary = new TenantBoundary(new TenancyProperties(TenancyMode.ENFORCE, Map.of()), registry, sink);
        MongoCollection<Document> users = TenantScopedCollection.wrap(raw, boundary);

        users.find(Filters.eq("email", "a@b.c"));

        verify(raw).find(Filters.eq("email", "a@b.c"));
    }

    private static BsonDocument render(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }
}
