package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.HandbookRule;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HandbookPromptTest {

    @Test
    void anEmptyHandbookAddsNothing() {
        StringBuilder sb = new StringBuilder();

        HandbookPrompt.append(sb, agent(Set.of()));

        assertThat(sb).isEmpty();
    }

    @Test
    void aCallWithoutAnAgentAddsNothing() {
        StringBuilder sb = new StringBuilder();

        HandbookPrompt.append(sb, null);

        assertThat(sb).isEmpty();
    }

    @Test
    void eachSwitchedOnRuleIsOneLineInEnumOrder() {
        StringBuilder sb = new StringBuilder();

        HandbookPrompt.append(sb, agent(Set.of(HandbookRule.STAY_ON_TOPIC, HandbookRule.CONFIRM_DETAILS)));

        assertThat(sb.toString()).isEqualTo(
                "- " + HandbookPrompt.line(HandbookRule.CONFIRM_DETAILS) + "\n"
                        + "- " + HandbookPrompt.line(HandbookRule.STAY_ON_TOPIC) + "\n");
    }

    @Test
    void everyRuleHasAPromptLine() {
        for (HandbookRule rule : HandbookRule.values()) {
            assertThat(HandbookPrompt.line(rule)).isNotBlank();
        }
    }

    private static AiAgent agent(Set<HandbookRule> handbook) {
        return AiAgent.builder().name("Agent").handbook(handbook).build();
    }
}
