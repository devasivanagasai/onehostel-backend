// Aggregator only. In a composite build a subprojects {} or all projects {} block here configures
// NOTHING in the included builds; shared configuration lives in build-logic convention plugins.
val libraryBuilds = setOf("onehostel-tenancy", "onehostel-common", "onehostel-scheduler-core")
val codeBuilds = gradle.includedBuilds.filter { it.name != "build-logic" }
val applicationBuilds = codeBuilds.filter { it.name !in libraryBuilds }

tasks.register("build") { dependsOn(codeBuilds.map { it.task(":build") }) }
tasks.register("test") { dependsOn(codeBuilds.map { it.task(":test") }) }
tasks.register("clean") { dependsOn(codeBuilds.map { it.task(":clean") }) }
tasks.register("bootJar") { dependsOn(applicationBuilds.map { it.task(":bootJar") }) }
tasks.register("publishLibraries") {
    dependsOn(codeBuilds.filter { it.name in libraryBuilds }.map { it.task(":publishToMavenLocal") })
}

// ./gradlew bootRun-onehostel-gateway, bootRun-onehostel-access-core, ...
applicationBuilds.forEach { build ->
    tasks.register("bootRun-${build.name}") { dependsOn(build.task(":bootRun")) }
}
