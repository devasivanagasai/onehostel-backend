import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    java
    jacoco
    id("io.spring.dependency-management")
    id("org.sonarqube")
}

group = "com.onehostel"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    // Published OneHostel libraries, for a build run outside the root composite
    mavenLocal {
        content { includeGroup("com.onehostel") }
    }
}

dependencyManagement {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

// Mockito loaded as a Java agent at test start-up (its documented setup for JDK 21+), instead of
// attaching itself at runtime, which is deprecated and fails on some Windows machines.
val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    mockitoAgent("org.mockito:mockito-core") { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Resolved at execution time, so aggregate runs (./gradlew test) do not resolve it unlocked.
    jvmArgumentProviders.add(CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.asPath}") })
    finalizedBy(tasks.named("jacocoTestReport"))
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required = true
        html.required = true
    }
}

// Coverage gate. Raise the floor per build with -PcoverageMinimum=0.70 (or in gradle.properties) as tests land.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                minimum = (findProperty("coverageMinimum") as String? ?: "0.0").toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}

// The token comes from -Dsonar.token or the SONAR_TOKEN environment variable, never from a build file.
sonar {
    properties {
        property("sonar.projectKey", project.name)
        property("sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path)
    }
}
