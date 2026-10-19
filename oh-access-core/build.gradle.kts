// Business service (organizations, hostels, ...). Reached only through oh-auth-gtwy, which signs
// the caller's identity onto every forwarded request.
plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":oh-common"))

    testImplementation("org.springframework.security:spring-security-test")
}
