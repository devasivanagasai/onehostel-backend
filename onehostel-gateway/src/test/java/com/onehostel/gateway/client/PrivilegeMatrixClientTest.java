package com.onehostel.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PrivilegeMatrixClientTest {

    private static final String URI = "http://access-core/internal/v1/privileges/matrix";
    private static final String BODY = """
            {"success":true,"responseObject":{"privileges":[{"code":"ROOMS_VIEW","bit":1,"apis":[{"method":"GET","path":"/api/v1/property/**"}]}],
             "roleTokens":{"HOSTEL_OWNER":"1"}},"statusCode":200}
            """;

    private MockRestServiceServer server;
    private MutableClock clock;
    private PrivilegeMatrixClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        clock = new MutableClock();
        client = new PrivilegeMatrixClient(builder.build(), URI, "s3cret", Duration.ofSeconds(60), clock);
    }

    @Test
    void fetchesWithTheInternalSecretAndReusesTheMatrixUntilItExpires() {
        server.expect(ExpectedCount.once(), requestTo(URI)).andExpect(header("X-Internal-Secret", "s3cret"))
                .andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));

        assertThat(client.current()).hasValueSatisfying(m -> assertThat(m.roleTokens()).containsEntry("HOSTEL_OWNER", "1"));
        clock.advance(Duration.ofSeconds(59));
        assertThat(client.current()).isPresent();
        server.verify();
    }

    @Test
    void keepsThePreviousMatrixWhenARefreshFails() {
        server.expect(requestTo(URI)).andRespond(withSuccess(BODY, MediaType.APPLICATION_JSON));
        server.expect(requestTo(URI)).andRespond(withServerError());

        assertThat(client.current()).isPresent();
        clock.advance(Duration.ofSeconds(61));
        assertThat(client.current()).hasValueSatisfying(m -> assertThat(m.privileges()).hasSize(1));
        server.verify();
    }

    @Test
    void noMatrixWhenTheFirstFetchFails() {
        server.expect(requestTo(URI)).andRespond(withServerError());

        assertThat(client.current()).isEmpty();
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-04T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
