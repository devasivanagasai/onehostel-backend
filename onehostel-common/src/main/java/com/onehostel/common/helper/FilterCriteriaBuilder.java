package com.onehostel.common.helper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.data.mapping.MappingException;
import org.springframework.data.mapping.PersistentPropertyPath;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.query.Criteria;

import com.onehostel.common.constants.CommonEnums.AndOrType;
import com.onehostel.common.constants.CommonEnums.FilterType;
import com.onehostel.common.exception.CommonErrorCode;
import com.onehostel.common.vo.FilterGroup;
import com.onehostel.common.vo.FilterQueryVO;

/**
 * Turns an issuanceSearch FilterGroup into Mongo criteria. Field names are checked against the
 * document class (no client-chosen field reaches Mongo unchecked), values are converted to the
 * field's type, and LIKE input is quoted, never used as a regex.
 */
public class FilterCriteriaBuilder {

    /** Properties that may never be filtered or sorted on, whatever the entity. */
    private static final List<String> NON_FILTERABLE = List.of("password", "passcode", "secret", "hash", "otp");
    private static final String RANGE_SEPARATOR = " to ";

    private final MongoMappingContext mappingContext;
    private final ZoneId zone;

    public FilterCriteriaBuilder(MongoMappingContext mappingContext, ZoneId zone) {
        this.mappingContext = mappingContext;
        this.zone = zone;
    }

    /** Criteria for the group; an empty Criteria when there is nothing to filter on. */
    public Criteria build(FilterGroup group, Class<?> entityClass) {
        if (group == null || group.isEmpty()) {
            return new Criteria();
        }
        List<Criteria> conditions = new ArrayList<>();
        for (FilterQueryVO filter : group.filterQueryVO()) {
            conditions.add(condition(filter, entityClass));
        }
        return group.andOrType() == AndOrType.OR
                ? new Criteria().orOperator(conditions)
                : new Criteria().andOperator(conditions);
    }

    /** Throws BAD_REQUEST unless the path is a filterable property of the entity. Returns its (element) type. */
    public Class<?> validateField(Class<?> entityClass, String path) {
        if (path == null || path.isBlank()
                || NON_FILTERABLE.stream().anyMatch(word -> path.toLowerCase(Locale.ROOT).contains(word))) {
            throw CommonErrorCode.BAD_REQUEST.raise("Unknown field: " + path);
        }
        try {
            PersistentPropertyPath<MongoPersistentProperty> property = mappingContext.getPersistentPropertyPath(path, entityClass);
            return property.getLeafProperty().getActualType();
        } catch (MappingException | IllegalArgumentException e) {
            throw CommonErrorCode.BAD_REQUEST.raise("Unknown field: " + path);
        }
    }

    private Criteria condition(FilterQueryVO filter, Class<?> entityClass) {
        String field = filter.filterfield();
        Class<?> type = validateField(entityClass, field);
        Object raw = filter.filtervalue();

        if (filter.filterType() == FilterType.DATE && raw != null) {
            return dateRange(field, type, raw.toString());
        }
        return switch (filter.operatorType()) {
            case EQUAL -> Criteria.where(field).is(convert(raw, type, field));
            case NOT_EQUAL -> Criteria.where(field).ne(convert(raw, type, field));
            case LIKE -> Criteria.where(field).regex(quoted(raw), "i");
            case IN -> Criteria.where(field).in(convertAll(raw, type, field));
            case NOT_IN -> Criteria.where(field).nin(convertAll(raw, type, field));
            case GREATER_THAN -> Criteria.where(field).gt(required(convert(raw, type, field), field));
            case GREATER_THAN_EQUAL -> Criteria.where(field).gte(required(convert(raw, type, field), field));
            case LESS_THAN -> Criteria.where(field).lt(required(convert(raw, type, field), field));
            case LESS_THAN_EQUAL -> Criteria.where(field).lte(required(convert(raw, type, field), field));
            case EQUAL_OR_NULL -> new Criteria().orOperator(
                    Criteria.where(field).is(convert(raw, type, field)), Criteria.where(field).is(null));
            case LIKE_OR_NULL -> new Criteria().orOperator(
                    Criteria.where(field).regex(quoted(raw), "i"), Criteria.where(field).is(null));
        };
    }

    /** "2026-01-01" or "2026-01-01 to 2026-01-31", inclusive, in the configured zone. */
    private Criteria dateRange(String field, Class<?> type, String value) {
        String[] parts = value.split(RANGE_SEPARATOR, 2);
        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(parts[0].trim());
            to = parts.length > 1 ? LocalDate.parse(parts[1].trim()) : from;
        } catch (RuntimeException e) {
            throw CommonErrorCode.BAD_REQUEST.raise("Invalid date range for " + field + ": use yyyy-MM-dd to yyyy-MM-dd");
        }
        if (type == LocalDate.class) {
            return Criteria.where(field).gte(from).lte(to);
        }
        if (type == LocalDateTime.class) {
            return Criteria.where(field).gte(from.atStartOfDay()).lt(to.plusDays(1).atStartOfDay());
        }
        return Criteria.where(field).gte(from.atStartOfDay(zone).toInstant()).lt(to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private List<Object> convertAll(Object raw, Class<?> type, String field) {
        Collection<?> values = raw instanceof Collection<?> collection
                ? collection
                : raw == null ? List.of() : Arrays.stream(raw.toString().split(",")).map(String::trim).toList();
        return values.stream().map(value -> convert(value, type, field)).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convert(Object raw, Class<?> type, String field) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        try {
            if (type == String.class) {
                return value;
            }
            if (type.isEnum()) {
                // Enums are stored by name; valueOf only checks the value exists
                return Enum.valueOf((Class<? extends Enum>) type, value).name();
            }
            if (type == Integer.class || type == int.class) {
                return Integer.valueOf(value);
            }
            if (type == Long.class || type == long.class) {
                return Long.valueOf(value);
            }
            if (type == Double.class || type == double.class) {
                return Double.valueOf(value);
            }
            if (type == BigDecimal.class) {
                return new BigDecimal(value);
            }
            if (type == Boolean.class || type == boolean.class) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(value);
                }
                return Boolean.valueOf(value);
            }
            if (type == Instant.class) {
                return value.length() == 10 ? LocalDate.parse(value).atStartOfDay(zone).toInstant() : Instant.parse(value);
            }
            if (type == LocalDate.class) {
                return LocalDate.parse(value);
            }
            if (type == LocalDateTime.class) {
                return LocalDateTime.parse(value);
            }
        } catch (RuntimeException e) {
            throw CommonErrorCode.BAD_REQUEST.raise("Invalid value for " + field + ": " + value);
        }
        return value;
    }

    private static Object required(Object value, String field) {
        if (value == null) {
            throw CommonErrorCode.BAD_REQUEST.raise("A value is required for " + field);
        }
        return value;
    }

    private static String quoted(Object raw) {
        return Pattern.quote(raw == null ? "" : raw.toString().trim());
    }
}
