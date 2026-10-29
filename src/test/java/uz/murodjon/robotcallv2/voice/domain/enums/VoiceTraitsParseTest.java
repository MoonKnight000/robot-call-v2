package uz.murodjon.robotcallv2.voice.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VoiceTraitsParseTest {

    @Test
    void eachVendorsGenderWordMapsToTheSameValue() {
        assertThat(VoiceGender.parse("female")).isEqualTo(VoiceGender.FEMALE);     // ElevenLabs
        assertThat(VoiceGender.parse("feminine")).isEqualTo(VoiceGender.FEMALE);   // Cartesia
        assertThat(VoiceGender.parse("FEMALE")).isEqualTo(VoiceGender.FEMALE);     // Gemini
        assertThat(VoiceGender.parse("masculine")).isEqualTo(VoiceGender.MALE);
        assertThat(VoiceGender.parse("gender_neutral")).isEqualTo(VoiceGender.NEUTRAL);
    }

    @Test
    void anUnknownOrMissingWordLeavesTheVoiceUndescribed() {
        assertThat(VoiceGender.parse(null)).isNull();
        assertThat(VoiceGender.parse("")).isNull();
        assertThat(VoiceGender.parse("robotic")).isNull();
        assertThat(VoiceAge.parse(null)).isNull();
        assertThat(VoiceAge.parse("ageless")).isNull();
    }

    @Test
    void elevenLabsAgeWordsMap() {
        assertThat(VoiceAge.parse("young")).isEqualTo(VoiceAge.YOUNG);
        assertThat(VoiceAge.parse("middle_aged")).isEqualTo(VoiceAge.MIDDLE_AGED);
        assertThat(VoiceAge.parse("middle aged")).isEqualTo(VoiceAge.MIDDLE_AGED);
        assertThat(VoiceAge.parse("old")).isEqualTo(VoiceAge.OLD);
    }
}
