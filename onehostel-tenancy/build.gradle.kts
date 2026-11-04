// Tenant context and the MongoDB tenant boundary. The lowest OneHostel library: depends on nothing of ours.
plugins {
    id("onehostel.library-conventions")
}

description = "OneHostel tenancy: TenantContext, tenant-aware MongoTemplate, resolution filter"

dependencies {
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.slf4j:slf4j-api")
    // Mongo and servlet pieces only activate where the service has them (gateway has neither Mongo nor tenants)
    compileOnly("org.springframework.boot:spring-boot-starter-data-mongodb")
    compileOnly("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
}
