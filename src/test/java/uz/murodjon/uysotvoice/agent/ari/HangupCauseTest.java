package uz.murodjon.uysotvoice.agent.ari;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This mapping decides whether a real number gets dialled again. Getting it wrong in the
 * lenient direction means calling a disconnected number three times; getting it wrong in the
 * strict direction means writing off a subscriber whose line was merely busy.
 */
class HangupCauseTest {

    @ParameterizedTest
    @CsvSource({
            "1,  WRONG_NUMBER",   // unallocated number
            "3,  WRONG_NUMBER",   // no route to destination
            "22, WRONG_NUMBER",   // number changed
            "17, NO_ANSWER",      // user busy
            "18, NO_ANSWER",      // no user responding
            "19, NO_ANSWER",      // no answer
            "20, NO_ANSWER",      // subscriber absent
            "21, REFUSED",        // call rejected
            "34, FAILED",         // no circuit available
            "38, FAILED",         // network out of order
            "42, FAILED",         // congestion
    })
    void mapsTheCausesThatCarryAVerdict(int cause, Disposition expected) {
        assertThat(HangupCause.toDisposition(cause)).isEqualTo(expected);
    }

    @Test
    void normalClearingCarriesNoVerdict() {
        // Cause 16 covers both "agreed to pay and said goodbye" and "hung up in anger".
        // Only the conversation knows which, so the mapping must not guess.
        assertThat(HangupCause.toDisposition(HangupCause.NORMAL_CLEARING)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 27, 99, 127})
    void unknownCausesCarryNoVerdict(int cause) {
        assertThat(HangupCause.toDisposition(cause)).isNull();
    }

    @Test
    void missingCauseIsHandled() {
        // An originate that never reached Stasis may report no cause at all.
        assertThat(HangupCause.toDisposition(null)).isNull();
        assertThat(HangupCause.label(null)).isNull();
        assertThat(HangupCause.isUnreachable(null)).isFalse();
    }

    @Test
    void unreachableIsOnlyTheNumberItself() {
        assertThat(HangupCause.isUnreachable(1)).isTrue();
        assertThat(HangupCause.isUnreachable(22)).isTrue();
        // A busy line is a working number.
        assertThat(HangupCause.isUnreachable(17)).isFalse();
        assertThat(HangupCause.isUnreachable(HangupCause.NORMAL_CLEARING)).isFalse();
    }

    @Test
    void labelKeepsTheCodeAndExplainsIt() {
        // The code goes into hangup_cause, and it is what someone reads months later when
        // asking why a target was never reached.
        assertThat(HangupCause.label(17)).isEqualTo("17 (user busy)");
        assertThat(HangupCause.label(99)).isEqualTo("99 (cause 99)");
    }
}
