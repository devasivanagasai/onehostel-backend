package com.onehostel.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Dummy secret so the context loads without a local .env (e.g. in CI)
@SpringBootTest(properties = "keycloak.service-client-secret=test-only")
class OhAuthGtwyApplicationTests {

    @Test
    void contextLoads() {
    }

}
