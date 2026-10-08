# syntax=docker/dockerfile:1.7
# Container image of the Te Tengo backend API (docs/DEPLOYMENT.md).
#
#   docker build -t te-tengo-general-api .
#   docker buildx build --platform linux/arm64,linux/amd64 -t te-tengo-general-api .
#
# The build stage runs on the build machine's own platform (the jar is platform independent), so a
# multi-platform build compiles once and only the runtime stage is assembled per platform.

ARG JAVA_VERSION=25

# 1) Build the executable jar and split it into Spring Boot layers.
FROM --platform=$BUILDPLATFORM eclipse-temurin:${JAVA_VERSION}-jdk AS build
WORKDIR /workspace
# Gradle wrapper and build scripts first: this layer is reused while only the sources change.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle/ gradle/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon --quiet dependencies --configuration runtimeClasspath > /dev/null
COPY src/main/ src/main/
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar \
 && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} application.jar \; \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# 2) Runtime: Temurin JRE, non-root user, one image layer per Spring Boot layer (dependencies change
#    far less often than the application, so pulls on the server are small).
FROM eclipse-temurin:${JAVA_VERSION}-jre
LABEL org.opencontainers.image.title="te-tengo-general-api" \
      org.opencontainers.image.description="Backend API of Te Tengo: fall detection alerts for older adults at home" \
      org.opencontainers.image.source="https://github.com/Te-Tengo-Tech/te-tengo-general-api" \
      org.opencontainers.image.vendor="Te Tengo"
RUN groupadd --system --gid 10001 tetengo \
 && useradd --system --uid 10001 --gid tetengo --home-dir /app --no-create-home --shell /usr/sbin/nologin tetengo
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
# Size the heap from the container memory limit and restart (through the orchestrator) on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Liveness probe without curl or wget (the JRE image has neither): plain HTTP over bash /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=3 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health/liveness HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && read -r l <&3 && [[ $l == *' 200 '* ]]"]
ENTRYPOINT ["java", "-jar", "application.jar"]
