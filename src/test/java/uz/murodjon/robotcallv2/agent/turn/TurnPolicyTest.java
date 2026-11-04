package uz.murodjon.robotcallv2.agent.turn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static uz.murodjon.robotcallv2.agent.turn.TurnCue.COMPLETE;
import static uz.murodjon.robotcallv2.agent.turn.TurnCue.INCOMPLETE;
import static uz.murodjon.robotcallv2.agent.turn.TurnCue.UNKNOWN;
import static uz.murodjon.robotcallv2.agent.turn.TurnCue.WAIT;

class TurnPolicyTest {

    private final TurnPolicy auto = AutoTurnPolicy.INSTANCE;
    private final TurnPolicy mixed = new PriorityTurnPolicy("mixed",
            List.of(TurnSource.LLM, TurnSource.WORDS, TurnSource.TURN_MODEL));

    @Test
    void autoReadsTheModelsVerdictOnTheWordsBeforeTheRules() {
        assertThat(auto.readWords(COMPLETE, INCOMPLETE)).isEqualTo(COMPLETE);
        assertThat(auto.readWords(UNKNOWN, INCOMPLETE)).isEqualTo(INCOMPLETE);
    }

    @Test
    void autoExtendsOnAnyUnfinishedSource() {
        assertThat(auto.judgePause(WAIT, COMPLETE, COMPLETE)).isEqualTo(new TurnCall(WAIT, TurnSource.LLM));
        assertThat(auto.judgePause(COMPLETE, INCOMPLETE, COMPLETE))
                .isEqualTo(new TurnCall(INCOMPLETE, TurnSource.TURN_MODEL));
        assertThat(auto.judgePause(UNKNOWN, COMPLETE, INCOMPLETE))
                .isEqualTo(new TurnCall(INCOMPLETE, TurnSource.WORDS));
    }

    @Test
    void autoLetsTheModelsFinishedOverruleTheRules() {
        // "… odam edimda": the rules read "-da" as unfinished, the model heard a finished sentence.
        assertThat(auto.judgePause(COMPLETE, UNKNOWN, INCOMPLETE)).isEqualTo(TurnCall.CLOSE);
        assertThat(auto.judgePause(UNKNOWN, UNKNOWN, UNKNOWN)).isEqualTo(TurnCall.CLOSE);
    }

    @Test
    void mixedLetsTheFirstSourceWithAVerdictDecide() {
        assertThat(mixed.judgePause(COMPLETE, INCOMPLETE, INCOMPLETE)).isEqualTo(new TurnCall(COMPLETE, TurnSource.LLM));
        assertThat(mixed.judgePause(UNKNOWN, INCOMPLETE, COMPLETE)).isEqualTo(new TurnCall(COMPLETE, TurnSource.WORDS));
        assertThat(mixed.judgePause(UNKNOWN, INCOMPLETE, UNKNOWN))
                .isEqualTo(new TurnCall(INCOMPLETE, TurnSource.TURN_MODEL));
        assertThat(mixed.judgePause(UNKNOWN, UNKNOWN, UNKNOWN)).isEqualTo(TurnCall.CLOSE);
    }

    @Test
    void aOneSourceOrderIgnoresTheOthers() {
        TurnPolicy turnModelOnly = new PriorityTurnPolicy("turn-model", List.of(TurnSource.TURN_MODEL));

        assertThat(turnModelOnly.readWords(INCOMPLETE, INCOMPLETE)).isEqualTo(UNKNOWN);
        assertThat(turnModelOnly.judgePause(WAIT, COMPLETE, INCOMPLETE))
                .isEqualTo(new TurnCall(COMPLETE, TurnSource.TURN_MODEL));
    }

    @Test
    void aShortWaitNeedsTheModelsFinishedNotJustTheRules() {
        assertThat(auto.allowsShortWait(COMPLETE, UNKNOWN)).isTrue();
        assertThat(auto.allowsShortWait(UNKNOWN, COMPLETE)).isFalse();
        assertThat(auto.allowsShortWait(UNKNOWN, UNKNOWN)).isFalse();

        assertThat(mixed.allowsShortWait(COMPLETE, INCOMPLETE)).isTrue();
        assertThat(mixed.allowsShortWait(UNKNOWN, COMPLETE)).isFalse();

        TurnPolicy wordsOnly = new PriorityTurnPolicy("words", List.of(TurnSource.WORDS));
        assertThat(wordsOnly.allowsShortWait(UNKNOWN, COMPLETE)).isTrue();

        TurnPolicy wordsFirst = new PriorityTurnPolicy("mixed", List.of(TurnSource.WORDS, TurnSource.LLM));
        assertThat(wordsFirst.allowsShortWait(INCOMPLETE, COMPLETE)).isFalse();
    }

    @Test
    void onlyAnUnfinishedOrWaitCallHoldsThePauseOpen() {
        assertThat(new TurnCall(INCOMPLETE, TurnSource.WORDS).holdsOpen()).isTrue();
        assertThat(new TurnCall(WAIT, TurnSource.LLM).holdsOpen()).isTrue();
        assertThat(new TurnCall(COMPLETE, TurnSource.LLM).holdsOpen()).isFalse();
        assertThat(TurnCall.CLOSE.holdsOpen()).isFalse();
    }
}
