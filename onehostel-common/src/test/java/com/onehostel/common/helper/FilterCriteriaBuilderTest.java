package com.onehostel.common.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

import com.onehostel.common.constants.CommonEnums.AndOrType;
import com.onehostel.common.constants.CommonEnums.FilterType;
import com.onehostel.common.constants.CommonEnums.OperatorEnum;
import com.onehostel.common.exception.HostelException;
import com.onehostel.common.model.BaseDocument;
import com.onehostel.common.vo.FilterGroup;
import com.onehostel.common.vo.FilterQueryVO;

class FilterCriteriaBuilderTest {

    enum Status { ACTIVE, INACTIVE }

    static class Room extends BaseDocument {
        String roomNo;
        Integer floor;
        Status status;
        String passwordHash;
        Address address;
    }

    static class Address {
        String city;
    }

    private final FilterCriteriaBuilder builder = new FilterCriteriaBuilder(mappingContext(), ZoneId.of("Asia/Kolkata"));

    /** Configured like Boot's: java.time types are simple values, not entities. */
    private static MongoMappingContext mappingContext() {
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(new MongoCustomConversions(List.of()).getSimpleTypeHolder());
        return context;
    }

    private Document build(FilterQueryVO... filters) {
        return builder.build(new FilterGroup(AndOrType.AND, List.of(filters)), Room.class).getCriteriaObject();
    }

    @Test
    void likeInputIsQuotedNotUsedAsARegex() {
        Document criteria = build(new FilterQueryVO("roomNo", "1.*", OperatorEnum.LIKE, null));

        Document condition = (Document) ((List<?>) criteria.get("$and")).get(0);
        assertThat(condition.get("roomNo").toString()).contains("\\Q1.*\\E");
    }

    @Test
    void valuesAreConvertedToTheFieldType() {
        Document criteria = build(new FilterQueryVO("floor", "2", OperatorEnum.GREATER_THAN_EQUAL, null),
                new FilterQueryVO("status", List.of("ACTIVE"), OperatorEnum.IN, null));

        List<?> conditions = (List<?>) criteria.get("$and");
        assertThat(((Document) conditions.get(0)).get("floor", Document.class).get("$gte")).isEqualTo(2);
        assertThat(((Document) conditions.get(1)).get("status", Document.class).get("$in")).isEqualTo(List.of("ACTIVE"));
    }

    @Test
    void nestedPathsAreAllowed() {
        Document criteria = build(new FilterQueryVO("address.city", "Hyderabad", OperatorEnum.EQUAL, null));

        assertThat(((Document) ((List<?>) criteria.get("$and")).get(0)).get("address.city")).isEqualTo("Hyderabad");
    }

    @Test
    void unknownSecretAndBadValuesAreRejected() {
        assertThatThrownBy(() -> build(new FilterQueryVO("$where", "1", OperatorEnum.EQUAL, null)))
                .isInstanceOf(HostelException.class).hasMessageContaining("Unknown field");
        assertThatThrownBy(() -> build(new FilterQueryVO("passwordHash", "x", OperatorEnum.EQUAL, null)))
                .isInstanceOf(HostelException.class).hasMessageContaining("Unknown field");
        assertThatThrownBy(() -> build(new FilterQueryVO("status", "DELETED", OperatorEnum.EQUAL, null)))
                .isInstanceOf(HostelException.class).hasMessageContaining("Invalid value for status");
        assertThatThrownBy(() -> build(new FilterQueryVO("floor", "two", OperatorEnum.EQUAL, null)))
                .isInstanceOf(HostelException.class).hasMessageContaining("Invalid value for floor");
    }

    @Test
    void dateRangeCoversWholeDaysInTheConfiguredZone() {
        Document criteria = build(new FilterQueryVO("createdAt", "2026-01-01 to 2026-01-31", OperatorEnum.EQUAL, FilterType.DATE));

        Document range = ((Document) ((List<?>) criteria.get("$and")).get(0)).get("createdAt", Document.class);
        assertThat(range.get("$gte")).isEqualTo(Instant.parse("2025-12-31T18:30:00Z"));
        assertThat(range.get("$lt")).isEqualTo(Instant.parse("2026-01-31T18:30:00Z"));
    }
}
