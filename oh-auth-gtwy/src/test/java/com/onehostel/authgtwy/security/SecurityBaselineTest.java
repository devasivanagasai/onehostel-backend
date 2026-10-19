package com.onehostel.security;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.onehostel.constants.ServiceConstants;

/**
 * Phase 2 checks: protected paths answer 401 in our JSON format, the correlation id is
 * always present (and never echoed when unsafe), API docs are public, and CORS only
 * accepts the configured Angular origin. No MongoDB server is needed for these tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({ "dev", "test" })
class SecurityBaselineTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedEndpointWithoutTokenReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.statusCode").value(401))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(header().exists(ServiceConstants.CORRELATION_ID_HEADER));
    }

    @Test
    void wellFormedCorrelationIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(ServiceConstants.CORRELATION_ID_HEADER, "test-correlation-123"))
                .andExpect(header().string(ServiceConstants.CORRELATION_ID_HEADER, "test-correlation-123"))
                .andExpect(jsonPath("$.correlationId").value("test-correlation-123"));
    }

    @Test
    void unsafeCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header(ServiceConstants.CORRELATION_ID_HEADER, "fake\" level=ERROR msg=hacked"))
                .andExpect(header().string(ServiceConstants.CORRELATION_ID_HEADER, matchesPattern("[a-f0-9]{32}")));
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }

    @Test
    void corsPreflightFromAngularOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
