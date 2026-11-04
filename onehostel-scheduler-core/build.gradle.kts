// Cluster-safe scheduled jobs: DB-held config, run history, item failure records, Mongo lease lock.
plugins {
    id("onehostel.library-conventions")
}

description = "OneHostel scheduler-core: HostelJob, run history, failure records, cluster lock"

dependencies {
    api(libs.onehostel.tenancy)
    api("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation(libs.ulid.creator)
}
