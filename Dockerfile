# syntax=docker/dockerfile:1.7
# Multi-stage build for the uysot-voice Spring Boot app.
# Lets the app run inside the same Docker network as Asterisk, so the
# externalMedia RTP path works on Docker Desktop (Windows/Mac) — no host
# networking needed.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Gradle state lives outside /src so it can be mounted as a BuildKit cache: the
# downloaded dependency graph then survives across builds even when
# build.gradle.kts changes and the layer cache below is invalidated.
ENV GRADLE_USER_HOME=/gradle-home

# Resolving dependencies is the slow part of the build and changes far less often than
# the source. Copying the build files on their own first lets Docker reuse that layer
# on every build where only src/ changed — previously a one-line code edit re-downloaded
# the whole dependency graph.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle gradle
RUN chmod +x gradlew
RUN --mount=type=cache,target=/gradle-home,sharing=locked \
    ./gradlew --no-daemon -q dependencies > /dev/null 2>&1 || true

# build/ and .gradle/ are cache mounts too, so javac/Lombok run incrementally
# instead of recompiling every class on every build. That also means no `clean`:
# Gradle's up-to-date checks decide what actually needs rebuilding. The jar is
# copied to /out because cache mounts are not part of the resulting layer.
COPY src src
RUN --mount=type=cache,target=/gradle-home,sharing=locked \
    --mount=type=cache,target=/src/build,sharing=locked \
    --mount=type=cache,target=/src/.gradle,sharing=locked \
    ./gradlew --no-daemon --build-cache bootJar \
    && mkdir -p /out \
    && cp "$(ls -1 build/libs/*.jar | grep -v -- '-plain.jar' | head -n1)" /out/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /out/app.jar /app/app.jar
EXPOSE 8080
# Heap follows the container limit instead of the JVM's conservative 25% default.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
