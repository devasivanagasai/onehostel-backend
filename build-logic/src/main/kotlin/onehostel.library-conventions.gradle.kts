plugins {
    id("onehostel.java-conventions")
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // Publish the resolved versions, so consumers outside the Boot BOM get the same ones
            versionMapping {
                allVariants { fromResolutionResult() }
            }
        }
    }
    repositories {
        // Nexus when NEXUS_URL is set (CI); otherwise publishToMavenLocal is the target
        System.getenv("NEXUS_URL")?.let { nexusUrl ->
            maven {
                name = "nexus"
                url = uri(nexusUrl)
                credentials {
                    username = System.getenv("NEXUS_USERNAME")
                    password = System.getenv("NEXUS_PASSWORD")
                }
            }
        }
    }
}
