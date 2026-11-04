// Cross-cutting code for every service: error catalogue, envelopes, base document, generic CRUD,
// correlation id, current user, audit trail, async executors.
plugins {
    id("onehostel.library-conventions")
}

description = "OneHostel common: errors, envelopes, generic CRUD, audit, correlation"

dependencies {
    api(libs.onehostel.tenancy)
    api("org.springframework.boot:spring-boot-starter-web")
    api("org.springframework.boot:spring-boot-starter-validation")
    implementation(libs.ulid.creator)

    // Mongo-backed pieces (CRUD base, ULID ids, auditing, audit log) only activate in services that use Mongo
    compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb")
    compileOnly("org.springframework.boot:spring-boot-starter-aop")
    compileOnly("com.github.ben-manes.caffeine:caffeine")
    compileOnly("org.springframework:spring-context-support")

    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    testImplementation("org.springframework.boot:spring-boot-starter-aop")
    testImplementation("com.github.ben-manes.caffeine:caffeine")
    testImplementation("org.springframework:spring-context-support")
}
