package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TurnSignalFactoryTest {

    private final SmartTurnDetector smartTurn = mock(SmartTurnDetector.class);
    private final TextTurnDetector textTurn = mock(TextTurnDetector.class);

    @SuppressWarnings("unchecked")
    private TurnSignalFactory factory(boolean smartEnabled, boolean textEnabled) {
        when(smartTurn.available()).thenReturn(true);
        when(smartTurn.samples()).thenReturn(16000);
        when(textTurn.available()).thenReturn(true);
        ObjectProvider<SmartTurnDetector> smartProvider = mock(ObjectProvider.class);
        ObjectProvider<TextTurnDetector> textProvider = mock(ObjectProvider.class);
        when(smartProvider.getIfAvailable()).thenReturn(smartTurn);
        when(textProvider.getIfAvailable()).thenReturn(textTurn);
        SmartTurnProperties smart = new SmartTurnProperties(smartEnabled, "m.onnx", List.of("ru-RU"), List.of("uz-UZ"),
                0.5f, 500, 0, 0.9f, 400, 160, 80, 800);
        TextTurnProperties text = new TextTurnProperties(textEnabled, "t.onnx", "tok.json", List.of("en-US"),
                0.5f, 600, 0, 0.9f, 128);
        return new TurnSignalFactory(smart, smartProvider, text, textProvider);
    }

    @Test
    void smartTurnDecidesALanguageItWasTrainedFor() {
        ActiveTurnSignal active = factory(true, true).openActive("ru-RU");

        assertThat(active.signal().name()).isEqualTo("smart-turn");
        assertThat(active.signal().audioListener()).isNotNull();
        assertThat(active.maxExtendMs()).isEqualTo(500);
    }

    @Test
    void theTextModelTakesALanguageSmartTurnDoesNotHave() {
        ActiveTurnSignal active = factory(true, true).openActive("en-US");

        assertThat(active.signal().name()).isEqualTo("text-turn");
        assertThat(active.signal().audioListener()).isNull();
        assertThat(active.maxExtendMs()).isEqualTo(600);
    }

    @Test
    void aLanguageNoModelCoversHasNoActiveSignalButMayHaveAShadow() {
        TurnSignalFactory factory = factory(true, true);

        assertThat(factory.openActive("uz-UZ")).isNull();
        assertThat(factory.openShadow("uz-UZ").name()).isEqualTo("smart-turn");
        assertThat(factory.openShadow("kk-KZ")).isNull();
    }

    @Test
    void switchedOffModelsOpenNothing() {
        TurnSignalFactory factory = factory(false, false);

        assertThat(factory.openActive("ru-RU")).isNull();
        assertThat(factory.openShadow("uz-UZ")).isNull();
    }

    @Test
    void theTextSignalScoresTheInterim() {
        when(textTurn.completion("да, всё понятно")).thenReturn(0.95f);
        ActiveTurnSignal active = factory(true, true).openActive("en-US");

        TurnOpinion opinion = active.signal().judge(new TurnMoment("да, всё понятно", ""));

        assertThat(opinion.cue()).isEqualTo(TurnCue.COMPLETE);
        assertThat(opinion.confident()).isTrue();
    }
}
