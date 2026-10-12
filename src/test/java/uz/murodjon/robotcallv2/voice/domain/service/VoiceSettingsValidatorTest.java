package uz.murodjon.robotcallv2.voice.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import static org.assertj.core.api.Assertions.*;

class VoiceSettingsValidatorTest {

    @Test
    void noSpeedMeansTheDefault() {
        assertThatCode(() -> VoiceSettingsValidator.validateSpeed(null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 1.0, 3.0})
    void everySpeedEverySynthesizerAcceptsIsAllowed(double speed) {
        assertThatCode(() -> VoiceSettingsValidator.validateSpeed(speed)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.24, 3.01, 10.0})
    void aSpeedSomeSynthesizerRejectsIsRefused(double speed) {
        assertThatThrownBy(() -> VoiceSettingsValidator.validateSpeed(speed))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.VOICE_SPEED_OUT_OF_RANGE));
    }
}
