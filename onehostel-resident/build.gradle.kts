plugins {
    id("onehostel.service-conventions")
}

description = "OneHostel resident: residents, admission, check-in/out, guardians"

dependencies {
    implementation(libs.onehostel.common)
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-cache")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation(libs.springdoc.webmvc.ui)

    testImplementation(libs.archunit.junit5)
}
