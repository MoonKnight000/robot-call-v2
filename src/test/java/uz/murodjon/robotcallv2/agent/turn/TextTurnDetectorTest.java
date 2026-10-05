package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Without its model the detector must leave every call on its timer: never extend a wait,
 * never cut a caller off.
 */
class TextTurnDetectorTest {

    @Test
    void withoutAModelItNeitherExtendsNorClosesEarly() {
        TextTurnDetector detector = new TextTurnDetector(new TextTurnProperties(true, "", "",
                List.of("ru-RU"), 0.5f, 600, 300, 0.9f, 128));
        detector.init();

        assertThat(detector.available()).isFalse();
        assertThat(detector.isComplete("я хотел сказать, что")).isTrue();
        assertThat(detector.isConfidentlyComplete("да, всё понятно")).isFalse();
    }

    @Test
    void onlyTheListedLanguagesAreTrusted() {
        TextTurnProperties properties = new TextTurnProperties(true, "m", "t",
                List.of("ru-RU"), 0.5f, 600, 0, 0.9f, 128);

        assertThat(properties.supports("ru-RU")).isTrue();
        assertThat(properties.supports("uz-UZ")).isFalse();
    }
}
