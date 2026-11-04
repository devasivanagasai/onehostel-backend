plugins {
    id("onehostel.service-conventions")
}

description = "OneHostel gateway: JWT verification, identity headers, rate limits, routing"

dependencyManagement {
    imports {
        mavenBom(libs.spring.cloud.dependencies.get().toString())
    }
}

dependencies {
    implementation(libs.onehostel.common)
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation(libs.bucket4j.core)

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation(libs.archunit.junit5)
}
