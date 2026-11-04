plugins {
    id("onehostel.java-conventions")
    id("org.springframework.boot")
}

springBoot {
    // Exposes build info at /actuator/info
    buildInfo()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // Run from the repo root so every service finds the shared .env (spring.config.import)
    workingDir = rootDir.parentFile
}
