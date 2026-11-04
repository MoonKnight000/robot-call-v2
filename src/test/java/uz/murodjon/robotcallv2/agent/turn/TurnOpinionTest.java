package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The opinion answers exactly what the detectors' isComplete / isConfidentlyComplete did. */
class TurnOpinionTest {

    @Test
    void aFinishedScoreAllowsTheCloseAndASureOneClosesEarly() {
        TurnOpinion opinion = TurnOpinion.fromProbability(0.95f, 0.5f, 0.9f);

        assertThat(opinion.cue()).isEqualTo(TurnCue.COMPLETE);
        assertThat(opinion.allowsClose()).isTrue();
        assertThat(opinion.confident()).isTrue();
    }

    @Test
    void anUnfinishedScoreHoldsTheClose() {
        TurnOpinion opinion = TurnOpinion.fromProbability(0.3f, 0.5f, 0.9f);

        assertThat(opinion.cue()).isEqualTo(TurnCue.INCOMPLETE);
        assertThat(opinion.allowsClose()).isFalse();
        assertThat(opinion.confident()).isFalse();
    }

    @Test
    void finishedButNotSureClosesOnlyWhenTheWaitRunsOut() {
        TurnOpinion opinion = TurnOpinion.fromProbability(0.7f, 0.5f, 0.9f);

        assertThat(opinion.allowsClose()).isTrue();
        assertThat(opinion.confident()).isFalse();
    }

    @Test
    void noScoreLeavesTheTimerAloneAndNeverClosesEarly() {
        TurnOpinion opinion = TurnOpinion.fromProbability(-1f, 0.5f, 0.9f);

        assertThat(opinion).isEqualTo(TurnOpinion.UNKNOWN);
        assertThat(opinion.allowsClose()).isTrue();
        assertThat(opinion.confident()).isFalse();
    }
}
