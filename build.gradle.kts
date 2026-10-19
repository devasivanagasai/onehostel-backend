// OneHostel backend: three Gradle modules in one repository.
//   oh-common      shared library (envelopes, errors, constants, security helpers, search mirror)
//   oh-auth-gtwy   the only public entry point: gateway + authentication (port 8080)
//   oh-access-core business service behind the gateway (port 8082)
plugins {
    id("org.springframework.boot") version "3.5.9" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

val springCloudVersion = "2025.0.0"

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.spring.dependency-management")

    group = "com.onehostel"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    extensions.configure<io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension> {
        imports {
            mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
            mavenBom("org.springframework.cloud:spring-cloud-dependencies:$springCloudVersion")
        }
    }

    // Mockito loaded as a Java agent at test start-up (its documented setup for JDK 21+), instead of
    // attaching itself at runtime, which is deprecated and fails on some Windows machines.
    val mockitoAgent = configurations.create("mockitoAgent")

    dependencies {
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "mockitoAgent"("org.mockito:mockito-core") { isTransitive = false }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("-javaagent:${mockitoAgent.asPath}", "-Xmx512m")
    }
}
