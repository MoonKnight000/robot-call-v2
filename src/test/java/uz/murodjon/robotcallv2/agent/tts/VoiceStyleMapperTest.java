package uz.murodjon.robotcallv2.agent.tts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import static org.assertj.core.api.Assertions.assertThat;

/** One mood and one pace, as each engine is given them. */
class VoiceStyleMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static EffectiveVoiceSettings style(String role, Double speed) {
        return new EffectiveVoiceSettings("x", speed, null, role, null);
    }

    @Test
    void aNeutralTurnAtTheVoicesOwnPaceSendsNothing() {
        ObjectNode eleven = objectMapper.createObjectNode();
        ObjectNode cartesia = objectMapper.createObjectNode();

        VoiceStyleMapper.putElevenLabsVoiceSettings(eleven, style("neutral", 1.0));
        VoiceStyleMapper.putCartesiaGenerationConfig(cartesia, "sonic-3.6", style(null, null));

        assertThat(eleven.has("voice_settings")).isFalse();
        assertThat(cartesia.has("generation_config")).isFalse();
        assertThat(VoiceStyleMapper.findGeminiDirection(style("neutral", null))).isNull();
    }

    @Test
    void aCheerfulTurnIsLivelierOnElevenLabsAndItsSpeedStaysInRange() {
        ObjectNode root = objectMapper.createObjectNode();

        VoiceStyleMapper.putElevenLabsVoiceSettings(root, style("cheerful", 1.5));

        ObjectNode settings = (ObjectNode) root.get("voice_settings");
        assertThat(settings.get("stability").asDouble()).isLessThan(0.5);
        assertThat(settings.get("style").asDouble()).isGreaterThan(0.0);
        assertThat(settings.get("speed").asDouble()).isEqualTo(1.2);
    }

    @Test
    void cartesiaGetsItsNearestEmotionOnlyOnSonic3() {
        ObjectNode sonic3 = objectMapper.createObjectNode();
        ObjectNode sonic2 = objectMapper.createObjectNode();

        VoiceStyleMapper.putCartesiaGenerationConfig(sonic3, "sonic-3.6", style("friendly", 0.9));
        VoiceStyleMapper.putCartesiaGenerationConfig(sonic2, "sonic-2", style("friendly", 0.9));

        assertThat(sonic3.get("generation_config").get("emotion").asText()).isEqualTo("content");
        assertThat(sonic3.get("generation_config").get("speed").asDouble()).isEqualTo(0.9);
        assertThat(sonic2.has("generation_config")).isFalse();
    }

    @Test
    void geminiIsDirectedInWords() {
        assertThat(VoiceStyleMapper.findGeminiDirection(style("friendly", 1.1)))
                .isEqualTo("Say in a warm, friendly and caring tone, a little faster than usual: ");
        assertThat(VoiceStyleMapper.findGeminiDirection(style(null, 0.9)))
                .isEqualTo("Say a little slower than usual: ");
    }
}
