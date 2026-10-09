package uz.murodjon.robotcallv2.aiagent.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentRollout;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRolloutRouterTest {

    private static final String STAGING = "+998900000001";

    @Test
    void aStagingNumberAlwaysGetsTheCandidate() {
        assertThat(AgentRolloutRouter.routesToCandidate(rollout(0), STAGING)).isTrue();
    }

    @Test
    void aWithheldNumberStaysOnTheLiveVersion() {
        assertThat(AgentRolloutRouter.routesToCandidate(rollout(100), null)).isFalse();
    }

    @Test
    void theSameNumberAlwaysGetsTheSameVersion() {
        AgentRollout rollout = rollout(50);
        boolean first = AgentRolloutRouter.routesToCandidate(rollout, "+998901234567");
        for (int i = 0; i < 10; i++) {
            assertThat(AgentRolloutRouter.routesToCandidate(rollout, "+998901234567")).isEqualTo(first);
        }
    }

    @Test
    void theShareOfCallersFollowsThePercentage() {
        AgentRollout rollout = rollout(10);
        int candidate = 0;
        for (int i = 0; i < 10_000; i++) {
            if (AgentRolloutRouter.routesToCandidate(rollout, "+99890" + String.format("%07d", i))) {
                candidate++;
            }
        }
        assertThat(candidate).isBetween(800, 1200);
    }

    @Test
    void zeroAndHundredPercentAreExact() {
        assertThat(AgentRolloutRouter.routesToCandidate(rollout(0), "+998901234567")).isFalse();
        assertThat(AgentRolloutRouter.routesToCandidate(rollout(100), "+998901234567")).isTrue();
    }

    private static AgentRollout rollout(int trafficPercent) {
        return new AgentRollout(7L, 1L, 2, trafficPercent, List.of(STAGING), Instant.now(), null);
    }
}
