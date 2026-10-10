package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiLiveProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiLiveProvider provider;

    @BeforeEach
    void setUp() {
        provider = new GeminiLiveProvider(null, objectMapper);
    }

    @Test
    void extendedThinkingModelIncludesThinkingLevelLowByDefault() throws Exception {
        RealtimeCallConfig config = new RealtimeCallConfig(
                "chan-1", "uz-UZ", "Prompt", null, List.of(),
                null, null, null, "gemini-3.8-live-extended-thinking"
        );
        GeminiLiveProperties live = new GeminiLiveProperties(
                "key", "wss://example.com", "gemini-3.8-live-extended-thinking", "Aoede",
                15, "LOW", null, 0, 0, 0, false, false, null
        );

        String json = provider.setupMessage(config, live, null);
        JsonNode root = objectMapper.readTree(json);

        JsonNode generationConfig = root.path("setup").path("generationConfig");
        assertThat(generationConfig.path("thinkingConfig").path("thinkingLevel").asText())
                .isEqualTo("LOW");
    }

    @Test
    void configuredThinkingLevelIsRespected() throws Exception {
        RealtimeCallConfig config = new RealtimeCallConfig(
                "chan-1", "uz-UZ", "Prompt", null, List.of(),
                null, null, null, "gemini-3.8-live-extended-thinking"
        );
        GeminiLiveProperties live = new GeminiLiveProperties(
                "key", "wss://example.com", "gemini-3.8-live-extended-thinking", "Aoede",
                15, "LOW", null, 0, 0, 0, false, false, "HIGH"
        );

        String json = provider.setupMessage(config, live, null);
        JsonNode root = objectMapper.readTree(json);

        JsonNode generationConfig = root.path("setup").path("generationConfig");
        assertThat(generationConfig.path("thinkingConfig").path("thinkingLevel").asText())
                .isEqualTo("HIGH");
    }

    @Test
    void nonThinkingModelWithoutConfiguredLevelOmitsThinkingConfig() throws Exception {
        RealtimeCallConfig config = new RealtimeCallConfig(
                "chan-1", "uz-UZ", "Prompt", null, List.of(),
                null, null, null, "gemini-3.1-flash-live-preview"
        );
        GeminiLiveProperties live = new GeminiLiveProperties(
                "key", "wss://example.com", "gemini-3.1-flash-live-preview", "Aoede",
                15, "LOW", null, 0, 0, 0, false, false, null
        );

        String json = provider.setupMessage(config, live, null);
        JsonNode root = objectMapper.readTree(json);

        JsonNode generationConfig = root.path("setup").path("generationConfig");
        assertThat(generationConfig.has("thinkingConfig")).isFalse();
    }
}
