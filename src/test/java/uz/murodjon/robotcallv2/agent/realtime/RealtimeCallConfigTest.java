package uz.murodjon.robotcallv2.agent.realtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One rule read by five providers: a scenario that named a model gets it, and one that did
 * not leaves the provider exactly where it was.
 */
class RealtimeCallConfigTest {

    @Test
    void aScenarioModelWinsOverTheProvidersOwn() {
        assertThat(withModel("gemini-2.5-flash-native-audio").modelOr("gemini-2.5-pro-preview"))
                .isEqualTo("gemini-2.5-flash-native-audio");
    }

    @Test
    void noScenarioModelLeavesTheProviderOnItsConfiguredOne() {
        assertThat(withModel(null).modelOr("gemini-2.5-pro-preview")).isEqualTo("gemini-2.5-pro-preview");
        assertThat(withModel("   ").modelOr("gemini-2.5-pro-preview")).isEqualTo("gemini-2.5-pro-preview");
    }

    @Test
    void aPipecatFallbackChainStillResolvesWhenNothingIsSet() {
        // Pipecat passes the company sub-engine as the fallback, which may itself be null.
        assertThat(withModel(null).modelOr(null)).isNull();
    }

    @Test
    void surroundingSpaceIsNotPartOfAModelName() {
        assertThat(withModel("  gpt-realtime  ").modelOr("fallback")).isEqualTo("gpt-realtime");
    }

    @Test
    void theShortConstructorLeavesTheModelUnset() {
        RealtimeCallConfig config =
                new RealtimeCallConfig("chan-1", "uz-UZ", "prompt", null, List.of());

        assertThat(config.model()).isNull();
        assertThat(config.modelOr("configured")).isEqualTo("configured");
    }

    private static RealtimeCallConfig withModel(String model) {
        return new RealtimeCallConfig("chan-1", "uz-UZ", "prompt", null, List.of(),
                null, null, null, model);
    }
}
