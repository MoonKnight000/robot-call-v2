# Multi-stage build for the uysot-voice Spring Boot app.
# Lets the app run inside the same Docker network as Asterisk, so the
# externalMedia RTP path works on Docker Desktop (Windows/Mac) — no host
# networking needed.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
