// uysot-voice — single-module Spring Boot application.
// All code lives under the uz.murodjon.uysotvoice base package.
plugins {
    java
    id("org.springframework.boot") version "3.5.14"
    id("io.spring.dependency-management") version "1.1.7"
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
}

// Boot 3.5.14 manages Flyway 11.7.2, which predates PostgreSQL 18 and rejects it
// ("Unsupported Database: PostgreSQL 18.4"). Override the BOM's managed version so
// flyway-core and flyway-database-postgresql stay aligned.
extra["flyway.version"] = "11.20.2"

// ari4java 0.18.0 requires the Netty 4.2 API (MultiThreadIoEventLoopGroup), so the
// whole graph moves off the BOM's 4.1 line together — netty.version is the Boot BOM
// property, so every io.netty artifact follows it. Watch reactor-netty (WebClient)
// and any non-shaded grpc-netty on the first run: those are pinned against 4.1 and
// are where a NoSuchMethodError would show up. Revert this + ari4java to 0.17.0 if so.
extra["netty.version"] = "4.2.0.Final"

// Spring AI BOM aligns the OpenAI starter and its transitive versions
// (PROJECT.md §2.3). If the Spring Boot 3.5 line needs a newer Spring AI, bump
// this version (Spring AI 1.0.x targets Boot 3.4.x).
dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:1.0.0")
    }
}

dependencies {
    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Asterisk ARI (bundles its own Netty-based HTTP/WebSocket client)
//    implementation("ch.loway.oss.ari4java:ari4java:0.9.0")
// Source: https://mvnrepository.com/artifact/io.github.ari4java/ari4java
    // 0.18.0 is built against Netty 4.2 (NettyHttpClient needs
    // io.netty.channel.MultiThreadIoEventLoopGroup), which the Boot 3.5 BOM does not
    // ship — it pins the 4.1 line. Hence the netty.version override above; without it
    // ARI.build() dies with NoClassDefFoundError, which is what sent us back to 0.17.0
    // the first time. We run Asterisk 20 / ARI 8.0.0 with AriVersion.IM_FEELING_LUCKY.
    implementation("io.github.ari4java:ari4java:0.18.0")
    // RTP audio transport over UDP. Only netty-transport is needed (NIO datagram
    // channel + handler); version is managed by the Spring Boot BOM. We avoid the
    // fat netty-all artifact so the vulnerable HTTP/HTTP2/SMTP codecs are not pulled in.
    implementation("io.netty:netty-transport")

    // STT — Google Cloud Speech-to-Text (streaming). Uses Application Default
    // Credentials (GOOGLE_APPLICATION_CREDENTIALS). Abstracted behind SttProvider.
    implementation("com.google.cloud:google-cloud-speech:4.36.0")

    // STT — Yandex SpeechKit v3 streaming (gRPC). The Yandex Cloud Java SDK bundles
    // the generated stubs (yandex.cloud.api.ai.stt.v3.RecognizerGrpc / Stt). v3
    // supports Uzbek (uz-UZ). gRPC transport comes transitively (also via google-cloud-speech).
    implementation("com.yandex.cloud:java-sdk-services:2.10.0")

    // TTS — Google Cloud Text-to-Speech (uz-UZ, and ru-RU fallback). Same ADC as
    // STT. Yandex SpeechKit (ru-RU) is reached over its REST API via the JDK
    // HttpClient, so it needs no extra dependency. Abstracted behind TtsProvider.
    implementation("com.google.cloud:google-cloud-texttospeech:2.44.0")

    // LLM dialog — Spring AI OpenAI starter pointed straight at Gemini's own
    // OpenAI-compatible endpoint (generativelanguage.googleapis.com/v1beta/openai),
    // no gateway in between. Fast model for the conversation FSM, stronger model for
    // the summary (Stage 9). Needs only GEMINI_API_KEY.
    implementation("org.springframework.ai:spring-ai-starter-model-openai")

    // VAD — Silero voice-activity model (ONNX) for barge-in (PROJECT.md §2.3, §7.2).
    // The silero_vad.onnx model file is supplied at runtime (voice-agent.vad.model-path).
    implementation("com.microsoft.onnxruntime:onnxruntime:1.17.3")

    // Object storage — upload call recordings to MinIO/S3 (PROJECT.md §2.3, Stage 9).
    implementation("io.minio:minio:8.5.10")

    // Metrics — Micrometer Prometheus registry, exposed at /actuator/prometheus (Stage 12).
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Database
    implementation("org.flywaydb:flyway-core")
    // Flyway 10+ ships per-database support in separate modules; without this one
    // Flyway cannot identify a PostgreSQL connection at all.
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Utils
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    //Doc
    // NOTE: stay on the springdoc 2.8.x line — it targets Spring Boot 3.5 (2.8.17 is
    // built against 3.5.13). springdoc 3.x targets Boot 4 and drags in the modular
    // spring-boot-webmvc/-servlet/-jackson 4.x artifacts, whose auto-configurations
    // collide with Boot 3.5's spring-boot-autoconfigure (duplicate
    // 'conventionErrorViewResolver' bean → APPLICATION FAILED TO START).
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
