package uz.murodjon.robotcallv2.agent.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PipecatRealtimeProviderTest {

    private RealtimeProperties properties;
    private PipecatRealtimeProvider provider;

    @BeforeEach
    void setUp() {
        PipecatRealtimeProperties pipecatProps = new PipecatRealtimeProperties(
                "test-api-key",
                "https://api.pipecat.daily.co/v1/public",
                null,
                "phone-agent",
                "claude-3-5-haiku-20241022",
                16000,
                15
        );
        properties = new RealtimeProperties(
                true,
                "pipecat",
                true,
                true,
                0.15,
                300,
                15,
                null,
                null,
                null,
                null,
                pipecatProps
        );
        provider = new PipecatRealtimeProvider(properties);
        provider.init();
    }

    @Test
    void reportsExpectedName() {
        assertThat(provider.name()).isEqualTo("pipecat");
    }

    @Test
    void supportsAnyLanguage() {
        assertThat(provider.supports("uz-UZ")).isTrue();
        assertThat(provider.supports("ru-RU")).isTrue();
        assertThat(provider.supports("en-US")).isTrue();
    }

    @Test
    void returnsConfiguredSampleRates() {
        assertThat(provider.inputSampleRate()).isEqualTo(16000);
        assertThat(provider.outputSampleRate()).isEqualTo(16000);
    }

    @Test
    void supportsSubEngineConfigurationInCallConfig() {
        RealtimeCallConfig config = new RealtimeCallConfig(
                "chan-1",
                "uz-UZ",
                "Prompt",
                "voice-1",
                List.of(),
                "deepgram",
                "claude-3-5-haiku",
                "cartesia",
                null
        );
        assertThat(config.pipecatStt()).isEqualTo("deepgram");
        assertThat(config.pipecatLlm()).isEqualTo("claude-3-5-haiku");
        assertThat(config.pipecatTts()).isEqualTo("cartesia");
        // No scenario model, so the company's sub-engine is what the provider will send.
        assertThat(config.modelOr(config.pipecatLlm())).isEqualTo("claude-3-5-haiku");
    }

    @Test
    void declaresTheCallsToolsInTheHandshake() throws Exception {
        ToolDefinition definition = mock(ToolDefinition.class);
        when(definition.name()).thenReturn("endCall");
        when(definition.description()).thenReturn("End the call");
        when(definition.inputSchema()).thenReturn("{\"type\":\"object\",\"properties\":{}}");
        ToolCallback tool = mock(ToolCallback.class);
        when(tool.getToolDefinition()).thenReturn(definition);
        RealtimeCallConfig config = new RealtimeCallConfig("chan-1", "uz-UZ", "Prompt", null, List.of(tool));

        JsonNode handshake = new ObjectMapper().readTree(
                PipecatRealtimeProvider.handshakeMessage(config, properties.pipecat()));

        assertThat(handshake.path("tools").size()).isEqualTo(1);
        JsonNode declared = handshake.path("tools").get(0);
        assertThat(declared.path("type").asText()).isEqualTo("function");
        assertThat(declared.path("name").asText()).isEqualTo("endCall");
        assertThat(declared.path("description").asText()).isEqualTo("End the call");
        assertThat(declared.path("parameters").path("type").asText()).isEqualTo("object");
    }

    @Test
    void sendsNoToolListWhenTheCallHasNone() throws Exception {
        RealtimeCallConfig config = new RealtimeCallConfig("chan-1", "uz-UZ", "Prompt", null, List.of());

        JsonNode handshake = new ObjectMapper().readTree(
                PipecatRealtimeProvider.handshakeMessage(config, properties.pipecat()));

        assertThat(handshake.has("tools")).isFalse();
        assertThat(handshake.path("type").asText()).isEqualTo("session_init");
    }
}
