package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionChange;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentVersionDifferTest {

    @Test
    void twoEqualStatesHaveNoChanges() {
        AiAgent agent = AiAgentFixtures.agent(7L, 1L, 3L, "uz-UZ", null);

        assertThat(AgentVersionDiffer.diff(agent, agent.withPublishedVersion(4))).isEmpty();
    }

    @Test
    void theChangedSettingIsNamedByItsPathWithBothValues() {
        AiAgent before = AiAgentFixtures.agent(7L, 1L, 3L, "uz-UZ", null).withFirstMessage("Assalomu alaykum");
        AiAgent after = before.withFirstMessage("Salom");

        List<AgentVersionChange> changes = AgentVersionDiffer.diff(before, after);

        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().path()).endsWith("firstMessage");
        assertThat(changes.getFirst().before().asText()).isEqualTo("Assalomu alaykum");
        assertThat(changes.getFirst().after().asText()).isEqualTo("Salom");
    }

    @Test
    void theOnOffSwitchIsNotAChange() {
        AiAgent agent = AiAgentFixtures.agent(7L, 1L, 3L, "uz-UZ", null);

        assertThat(AgentVersionDiffer.diff(agent.withEnabled(true), agent.withEnabled(false))).isEmpty();
    }
}
