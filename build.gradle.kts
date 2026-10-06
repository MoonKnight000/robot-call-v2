import com.google.protobuf.gradle.id

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.google.protobuf") version "0.10.0"
}

group = "uz.murodjon"
version = "0.0.1-SNAPSHOT"
description = "AI voice agent — automatic outbound calling system"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["flyway.version"] = "13.4.0"
// netty.version is deliberately NOT pinned. Boot 4.1.1 already manages 4.2.17.Final, and
// it does so by importing netty-bom — but netty-bom 4.2.0.Final lists no QUIC artifact at
// all, so pinning the property back to 4.2.0 left netty-handler at 4.2.0 while
// netty-codec-native-quic still resolved to 4.2.17 through reactor-netty. The newer
// QuicheQuicSslContext reads SslContext.defaultEndpointVerificationAlgorithm, a field the
// older netty-handler does not have, and the client blew up with NoSuchFieldError at the
// first HTTPS call. The pin dated from when Boot's default was still Netty 4.1 and
// ari4java 0.18.0 needed 4.2; the default is 4.2 now, so it only dragged Netty backwards.
extra["springAiVersion"] = "2.1.0-M1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
        mavenBom("io.grpc:grpc-bom:1.62.2")
        mavenBom("org.testcontainers:testcontainers-bom:1.20.4")
    }
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Scheduled report delivery (§10.10 "Jadval bo'yicha yuborish") — SMTP config read
    // from voice-agent.report-schedule.* (env-var placeholders, like every other
    // external credential in this project).
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // Jackson 2, declared explicitly. Boot 4 auto-configures Jackson 3 (tools.jackson)
    // instead, so com.fasterxml.jackson is on the classpath only because ari4java, minio
    // and jjwt-jackson happen to drag it in — and this code reads and writes every
    // JSONB column and every provider payload with it. Versions come from the Boot BOM
    // (jackson-2-bom), so if one of those libraries moves to Jackson 3 the build keeps
    // compiling instead of breaking in 60-odd files at once.
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Outbound calls to tenant-supplied URLs (shared/http). Apache HttpClient 5 because it
    // lets us plug in the DNS resolver: the address a socket connects to is the one
    // PublicUrlGuard vetted, not a second lookup an attacker's DNS can change (SSRF DNS
    // rebinding). Version from the Spring Boot BOM.
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // YAML OpenAPI documents for the tool importer (UNIVERSAL-OPERATOR-PLAN B.2). Spring Boot
    // already brings it in for application.yml; named here because this code uses it. Version from the BOM.
    implementation("org.yaml:snakeyaml")

    // JWT for user login (ROADMAP E.1) — X-Api-Key stays for machine-to-machine callers.
    // Versions for api, impl, and jackson are kept 100% in sync via jjwtVersion.
    val jjwtVersion = "0.13.0"
    implementation("io.jsonwebtoken:jjwt-api:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:$jjwtVersion")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:$jjwtVersion")

    // Asterisk ARI (bundles its own Netty-based HTTP/WebSocket client)
    // 0.18.0 is built against Netty 4.2 (NettyHttpClient needs
    // the first time. We run Asterisk 20 / ARI 8.0.0 with AriVersion.IM_FEELING_LUCKY.
    implementation("io.github.ari4java:ari4java:0.18.0")
    // RTP audio transport over UDP. Only netty-transport is needed (NIO datagram
    // channel + handler); version is managed by the Spring Boot BOM. We avoid the
    // fat netty-all artifact so the vulnerable HTTP/HTTP2/SMTP codecs are not pulled in.
    implementation("io.netty:netty-transport")

    // STT + TTS — Yandex SpeechKit v3 streaming (gRPC: RecognizeStreaming / UtteranceSynthesis).
    // Stubs (yandex.cloud.api.ai.{stt,tts}.v3.*) are generated at build time from the
    // .proto files vendored under src/main/proto (pulled from yandex-cloud/cloudapi) —
    // see the protobuf {} block below — instead of depending on the full
    // com.yandex.cloud:java-sdk-services SDK. v3 STT supports Uzbek (uz-UZ).
    implementation("io.grpc:grpc-netty-shaded")
    implementation("io.grpc:grpc-protobuf")
    implementation("io.grpc:grpc-stub")

    // LLM dialog — Spring AI's native Google GenAI starter (Gemini direct) and OpenAI-compatible starter (Groq ultra-low-latency LPU & OpenAI)
    implementation("org.springframework.ai:spring-ai-starter-model-google-genai")
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // VAD — Silero voice-activity model (ONNX) for barge-in (PROJECT.md §2.3, §7.2).
    // The silero_vad.onnx model file is supplied at runtime (voice-agent.vad.model-path).
    implementation("com.microsoft.onnxruntime:onnxruntime:1.17.3")
    // Tokenizer for the text end-of-turn model (NAMO, voice-agent.text-turn); reads its tokenizer.json.
    implementation("ai.djl.huggingface:tokenizers:0.33.0")

    // Object storage — upload call recordings to MinIO/S3 (PROJECT.md §2.3, Stage 9).
    implementation("io.minio:minio:8.6.0")

    // Metrics — Micrometer Prometheus registry, exposed at /actuator/prometheus (Stage 12).
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Report export (§10.10 "Hisobotni yuklab olish") — PDF and XLSX, alongside the
    // existing plain-CSV path.
    implementation("com.github.librepdf:openpdf:1.4.2")
    implementation("org.apache.poi:poi-ooxml:5.4.0")

    // Database — Flyway migrations and PostgreSQL
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    //Doc
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    // CLAUDE.md layering rules checked by the build (ArchitectureTest), not only in review.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Tests that need Docker (Testcontainers) or a full Spring context. CI runs everything
// else as `unitTest`; these stay with the plain `test` task, run by hand.
val integrationTests = listOf(
    "uz/murodjon/robotcallv2/RobotCallV2ApplicationTests.class",
    "uz/murodjon/robotcallv2/company/CompanyIsolationTest.class",
    "uz/murodjon/robotcallv2/campaign/repository/CampaignTargetRepositoryTest.class",
)

val unitTest by tasks.registering(Test::class) {
    description = "Runs the tests that need neither Docker nor a Spring context."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    exclude(integrationTests)
    finalizedBy("jacocoUnitTestReport")
}

jacoco {
    toolVersion = "0.8.13"
}

// Coverage of the unit suite. Generated gRPC stubs (yandex.*) are not our code.
tasks.register<JacocoReport>("jacocoUnitTestReport") {
    description = "Coverage report for the unitTest task."
    group = "verification"
    executionData(unitTest.get())
    sourceSets(sourceSets.main.get())
    classDirectories.setFrom(sourceSets.main.get().output.asFileTree.matching {
        include("uz/murodjon/**")
    })
    reports {
        html.required = true
        xml.required = true
    }
}

// Offline turn-boundary replay over recorded calls (TurnReplayTool), used by CI's
// turn-replay job: ./gradlew turnReplay --args="corpus --vad-model=... --manifest=..."
tasks.register<JavaExec>("turnReplay") {
    description = "Replays recordings through VAD + turn detection and reports the turns."
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "uz.murodjon.robotcallv2.agent.turn.TurnReplayTool"
}

// Generates Java + gRPC stubs from the .proto files vendored under src/main/proto
// (Yandex SpeechKit STT/TTS v3, pulled from yandex-cloud/cloudapi). Keeps the build
// self-contained instead of depending on the full com.yandex.cloud:java-sdk-services SDK.
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.62.2"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                maybeCreate("grpc")
            }
        }
    }
}
