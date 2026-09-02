package uz.murodjon.robotcallv2;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Boots the full application context against a throwaway PostgreSQL container.
 * Passing this test proves the Flyway migrations apply cleanly (Stage 0 check).
 * RabbitMQ and Redis connect lazily, so no brokers are needed here.
 */
@SpringBootTest
@Testcontainers
class RobotCallV2ApplicationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void disableExternalBrokers(DynamicPropertyRegistry registry) {
        // Redis/Rabbit are not available in this test; keep them off the health probes and listeners.
        registry.add("management.health.redis.enabled", () -> "false");
        registry.add("management.health.rabbit.enabled", () -> "false");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
        // No Asterisk in this test — do not open the ARI connection.
        registry.add("voice-agent.asterisk.enabled", () -> "false");
        // No Google credentials in this test — do not create the STT client.
        registry.add("voice-agent.stt.enabled", () -> "false");
        registry.add("spring.ai.google.genai.api-key", () -> "test-api-key");
        registry.add("spring.ai.google.genai.chat.api-key", () -> "test-api-key");
        registry.add("spring.ai.gemini.chat.api-key", () -> "test-api-key");
        registry.add("spring.ai.openai.api-key", () -> "test-api-key");
    }

    @Test
    void contextLoads() {
    }
}
