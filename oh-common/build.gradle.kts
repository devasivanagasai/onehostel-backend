// Shared library: no application of its own, used by oh-auth-gtwy and oh-access-core.
plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-security")
    api("org.springframework.boot:spring-boot-starter-data-mongodb")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")
}
