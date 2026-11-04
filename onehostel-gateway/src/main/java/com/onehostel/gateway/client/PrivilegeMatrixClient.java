package com.onehostel.gateway.client;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.onehostel.gateway.dto.PrivilegeMatrix;
import com.onehostel.tenancy.constants.IdentityHeaders;

import lombok.extern.slf4j.Slf4j;

/**
 * Fetches access-core's privilege matrix and keeps it for {@code ttl}. When access-core cannot be reached
 * the last matrix stays in use (a role change made meanwhile applies once it is back); with no matrix at
 * all there is nothing to authorize against, and callers refuse the request.
 */
@Slf4j
public class PrivilegeMatrixClient {

    /** After a failed fetch, wait this long before trying again, so an outage is not hit once per request. */
    private static final Duration RETRY_AFTER_FAILURE = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String uri;
    private final String internalSecret;
    private final Duration ttl;
    private final Clock clock;

    private volatile Snapshot snapshot;

    private record Snapshot(PrivilegeMatrix matrix, Instant refreshAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Envelope(PrivilegeMatrix responseObject) {
    }

    public PrivilegeMatrixClient(RestClient restClient, String uri, String internalSecret, Duration ttl, Clock clock) {
        this.restClient = restClient;
        this.uri = uri;
        this.internalSecret = internalSecret;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Optional<PrivilegeMatrix> current() {
        Snapshot cached = snapshot;
        if (cached != null && clock.instant().isBefore(cached.refreshAt())) {
            return Optional.of(cached.matrix());
        }
        synchronized (this) {
            cached = snapshot;
            if (cached != null && clock.instant().isBefore(cached.refreshAt())) {
                return Optional.of(cached.matrix());
            }
            try {
                PrivilegeMatrix matrix = fetch();
                snapshot = new Snapshot(matrix, clock.instant().plus(ttl));
                return Optional.of(matrix);
            } catch (RestClientException e) {
                if (cached == null) {
                    log.error("Privilege matrix unavailable from {}; refusing API calls until it is", uri, e);
                    return Optional.empty();
                }
                log.warn("Privilege matrix refresh from {} failed; keeping the previous one", uri, e);
                snapshot = new Snapshot(cached.matrix(), clock.instant().plus(RETRY_AFTER_FAILURE));
                return Optional.of(cached.matrix());
            }
        }
    }

    private PrivilegeMatrix fetch() {
        Envelope envelope = restClient.get().uri(uri)
                .headers(headers -> {
                    if (internalSecret != null) {
                        headers.set(IdentityHeaders.INTERNAL_SECRET, internalSecret);
                    }
                })
                .retrieve()
                .body(new ParameterizedTypeReference<Envelope>() {
                });
        if (envelope == null || envelope.responseObject() == null) {
            throw new RestClientException("Empty privilege matrix from " + uri);
        }
        return envelope.responseObject();
    }
}
