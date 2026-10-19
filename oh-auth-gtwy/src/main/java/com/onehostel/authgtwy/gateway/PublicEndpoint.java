package com.onehostel.authgtwy.gateway;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.common.constants.CollectionNames;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A path the gateway lets through without a token (permit-all). The list lives in the database so
 * it can change without a redeploy; PublicEndpointRegistry holds the active copy in memory.
 *
 * {@code system} entries are the built-in ones (login, refresh, health, ...), seeded at startup and
 * not removable, so the platform cannot lock itself out.
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = CollectionNames.PUBLIC_ENDPOINTS)
@CompoundIndex(name = "pattern_method_unique", def = "{'pattern': 1, 'method': 1}", unique = true)
public class PublicEndpoint {

    @Id
    private String id;

    /** Ant-style path pattern, e.g. {@code /api/v1/auth/login} or {@code /swagger-ui/**}. */
    private String pattern;

    /** HTTP method, or null for every method. */
    private String method;

    private String description;

    private boolean enabled = true;

    private boolean system;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public PublicEndpoint(String pattern, String method, String description, boolean system) {
        this.pattern = pattern;
        this.method = method;
        this.description = description;
        this.system = system;
    }
}
