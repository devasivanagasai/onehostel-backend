# One image recipe for every service:
#   ./gradlew bootJar
#   docker build --build-arg SERVICE=onehostel-gateway -t <registry>/<env>/onehostel-gateway:<build> .
# The profile is NOT baked in: pass SPRING_PROFILES_ACTIVE (and secrets) from the deployment.
FROM eclipse-temurin:21-jre-alpine

ARG SERVICE
RUN apk upgrade --no-cache \
    && addgroup -S onehostel && adduser -S onehostel -G onehostel

WORKDIR /app
COPY ${SERVICE}/build/libs/${SERVICE}-*-SNAPSHOT.jar app.jar
USER onehostel

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
