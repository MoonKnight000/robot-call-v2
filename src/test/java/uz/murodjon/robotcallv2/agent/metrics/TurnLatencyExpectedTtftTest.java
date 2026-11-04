package uz.murodjon.robotcallv2.agent.metrics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TurnLatencyExpectedTtftTest {

    @Test
    void nothingIsExpectedBeforeAnythingWasMeasured() {
        assertThat(new TurnLatency().expectedTtftMs()).isEqualTo(-1);
    }

    @Test
    void theWarmUpSeedsTheEstimateOnce() {
        TurnLatency latency = new TurnLatency();

        latency.warmupMeasured(2500);
        latency.warmupMeasured(400);

        assertThat(latency.expectedTtftMs()).isEqualTo(2500);
    }

    @Test
    void aSpeculatedReplyDoesNotMoveTheEstimate() {
        TurnLatency latency = new TurnLatency();
        latency.warmupMeasured(2500);

        latency.turnStarted();
        latency.speculationAdopted();
        latency.llmRequested();
        latency.llmFirstToken();

        assertThat(latency.isSpeculated()).isTrue();
        assertThat(latency.expectedTtftMs()).isEqualTo(2500);
    }

    @Test
    void aMeasuredTurnReplacesHalfOfTheEstimate() {
        TurnLatency latency = new TurnLatency();
        latency.warmupMeasured(3000);

        latency.turnStarted();
        latency.llmRequested();
        latency.llmFirstToken(); // a few ms at most

        assertThat(latency.expectedTtftMs()).isBetween(1500, 1600);
    }
}
