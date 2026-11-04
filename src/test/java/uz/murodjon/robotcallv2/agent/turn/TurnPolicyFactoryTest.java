package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TurnPolicyFactoryTest {

    private static TurnPolicyFactory factory(TurnEngine engine) {
        return new TurnPolicyFactory(new TurnTakingProperties(engine, null,
                Map.of("uz-UZ", List.of(TurnSource.LLM, TurnSource.WORDS))));
    }

    @Test
    void autoIsTheDefault() {
        TurnPolicyFactory factory = new TurnPolicyFactory(new TurnTakingProperties(null, null, null));

        assertThat(factory.findForLanguage("uz-UZ")).isSameAs(AutoTurnPolicy.INSTANCE);
    }

    @Test
    void aOneSourceEngineTrustsOnlyThatSource() {
        PriorityTurnPolicy policy = (PriorityTurnPolicy) factory(TurnEngine.LLM).findForLanguage("uz-UZ");

        assertThat(policy.name()).isEqualTo("llm");
        assertThat(policy.order()).containsExactly(TurnSource.LLM);
    }

    @Test
    void mixedTakesTheLanguagesOrderOrTheDefaultOne() {
        TurnPolicyFactory factory = factory(TurnEngine.MIXED);

        assertThat(((PriorityTurnPolicy) factory.findForLanguage("UZ-uz")).order())
                .containsExactly(TurnSource.LLM, TurnSource.WORDS);
        assertThat(((PriorityTurnPolicy) factory.findForLanguage("ru-RU")).order())
                .containsExactly(TurnSource.LLM, TurnSource.TURN_MODEL, TurnSource.WORDS);
    }
}
