package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import static org.assertj.core.api.Assertions.assertThat;

class AiAgentSnapshotTest {

    /**
     * The draft row's lock counter moves on every save; were it in the snapshot, an unchanged
     * draft would always look different from what was published.
     */
    @Test
    void theDraftRowsLockCounterIsNotPartOfTheSnapshot() {
        AiAgent agent = AiAgentFixtures.agent(7L, 1L, 3L, "uz-UZ", null);

        String savedFourTimes = AiAgentSnapshot.toJson(agent.withVersion(4));

        assertThat(savedFourTimes).isEqualTo(AiAgentSnapshot.toJson(agent.withVersion(9)));
        assertThat(AiAgentSnapshot.fromJson(savedFourTimes).version()).isZero();
    }
}
