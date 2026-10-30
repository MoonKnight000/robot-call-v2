package uz.murodjon.robotcallv2.aiagent.domain.entity;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingDirection;
import uz.murodjon.robotcallv2.aiagent.domain.enums.GreetingMode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentGreetingTest {

    private static final AgentGreeting NO_LINES = AgentGreeting.DEFAULT;

    @Test
    void theBaselineFirstMessageIsUsedWhenNothingMoreSpecificExists() {
        assertThat(NO_LINES.findLine("Salom", "uz-UZ", false, bound -> 0)).isEqualTo("Salom");
    }

    @Test
    void noLineAtAllLeavesTheOpeningToTheModel() {
        assertThat(NO_LINES.findLine(null, "uz-UZ", false, bound -> 0)).isNull();
        assertThat(NO_LINES.findLine("  ", "uz-UZ", false, bound -> 0)).isNull();
    }

    @Test
    void aLineAimedAtTheCallBeatsTheBaseline() {
        AgentGreeting greeting = new AgentGreeting(GreetingMode.AI_CUSTOM, null, List.of(
                new GreetingLine(GreetingDirection.INBOUND, "ru", "Здравствуйте, вы позвонили в компанию")));

        assertThat(greeting.findLine("Salom", "ru-RU", true, bound -> 0)).isEqualTo("Здравствуйте, вы позвонили в компанию");
        assertThat(greeting.findLine("Salom", "ru-RU", false, bound -> 0)).isEqualTo("Salom");
        assertThat(greeting.findLine("Salom", "uz-UZ", true, bound -> 0)).isEqualTo("Salom");
    }

    @Test
    void equallyAimedLinesAreVariantsAndOneIsPicked() {
        AgentGreeting greeting = new AgentGreeting(GreetingMode.AI_CUSTOM, null, List.of(
                new GreetingLine(null, null, "Salom!")));

        // The baseline and "Salom!" are both unaimed: the pick decides which one is heard.
        assertThat(greeting.findLine("Assalomu alaykum", "uz-UZ", false, bound -> bound - 1)).isEqualTo("Assalomu alaykum");
        assertThat(greeting.findLine("Assalomu alaykum", "uz-UZ", false, bound -> 0)).isEqualTo("Salom!");
    }

    @Test
    void aDynamicOrUserFirstGreetingNeverCarriesALine() {
        List<GreetingLine> lines = List.of(new GreetingLine(null, null, "Salom!"));

        assertThat(new AgentGreeting(GreetingMode.AI_DYNAMIC, null, lines).findLine("Salom", "uz", false, bound -> 0)).isNull();
        assertThat(new AgentGreeting(GreetingMode.USER_FIRST, null, lines).findLine("Salom", "uz", false, bound -> 0)).isNull();
    }
}
