// Convention plugins shared by every OneHostel build (src/main/kotlin/*.gradle.kts):
//   onehostel.java-conventions     Java 21, Boot BOM, Lombok, JUnit, JaCoCo, Sonar
//   onehostel.library-conventions  + java-library and maven-publish (Nexus or mavenLocal)
//   onehostel.service-conventions  + Spring Boot application
plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.plugin)
    implementation(libs.sonarqube.gradle.plugin)
}
