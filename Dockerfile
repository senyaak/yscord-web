# syntax=docker/dockerfile:1

# ---- Build stage: JDK builds the whole app; the node-gradle plugin downloads its
#      own Node and compiles the Angular frontend into the jar during bootJar. ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Prime backend dependencies first so code-only changes reuse the cache.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Angular sources + backend sources. buildFrontend (ng build) runs as part of
# processResources and lands the SPA in src/main/resources/static → into the jar.
COPY frontend ./frontend
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon clean bootJar

# ---- Runtime stage: slim JRE + yt-dlp for the audio proxy ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# yt-dlp standalone linux build — bundles its own Python, so no extra runtime deps.
# Grab the latest release so YouTube extraction keeps working over time.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && curl -fsSL https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux \
        -o /usr/local/bin/yt-dlp \
    && chmod a+rx /usr/local/bin/yt-dlp \
    && apt-get purge -y curl && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system spring && useradd --system --gid spring --uid 1001 spring
COPY --from=build /workspace/build/libs/*.jar app.jar
USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
