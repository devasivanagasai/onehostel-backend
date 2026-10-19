// The only public entry point: authentication (login, OTP, tokens, users) plus the gateway that
// checks every request and routes it to the business services.
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":oh-common"))

    // Spring Cloud Gateway on the servlet stack (Server MVC), so the blocking auth code runs in-process.
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")
    implementation("com.github.ben-manes.caffeine:caffeine")

    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.security:spring-security-test")
}
