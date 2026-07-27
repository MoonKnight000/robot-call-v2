package uz.murodjon.uysotvoice.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard sits between the model and the phone line, so both of its failure modes are
 * expensive: letting a wrong sum through means a caller is told they owe money they do not,
 * and blocking a correct sentence means the agent goes silent mid-call. These cases pin
 * both edges.
 */
class FactGuardTest {

    private static final CallContext CONTEXT = new CallContext(
            "Aziz Karimov", new BigDecimal("1500000"), "so'm",
            LocalDate.of(2026, 7, 1), "UY-2026-00123", "goal");

    @ParameterizedTest
    @ValueSource(strings = {
            "Qarzingiz 1500000 so'm.",
            "Qarzingiz 1 500 000 so'm.",       // spaces are how a model groups digits
            "Qarzingiz 1.500.000 so'm.",       // Uzbek/Russian grouping
            "Qarzingiz 1,500,000 so'm.",       // English grouping
    })
    void acceptsTheDebtAmountHoweverItIsWritten(String text) {
        assertThat(FactGuard.violations(text, CONTEXT)).isEmpty();
    }

    @Test
    void blocksAnAmountThatIsNotInTheFacts() {
        assertThat(FactGuard.violations("Qarzingiz 15000000 so'm.", CONTEXT))
                .containsExactly("15000000");
    }

    @Test
    void blocksADiscountItInventedAlongsideTheRealFigure() {
        // The prompt forbids offering a discount; this is the check that the caller never
        // hears one anyway.
        assertThat(FactGuard.violations(
                "Qarzingiz 1500000 so'm, lekin 1200000 to'lasangiz ham bo'ladi.", CONTEXT))
                .containsExactly("1200000");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "3 kun ichida to'lashingiz kerak.",       // small numbers are conversational
            "Kelasi oyning 5-sanasida to'laysizmi?",
            "2027-yil 15-avgustda to'lasangiz bo'ladi.",  // a year the client proposed
            "Shartnoma raqami UY-2026-00123.",       // reading a given fact back
            "Soat 14:30 da qo'ng'iroq qilaman.",
    })
    void leavesOrdinarySpeechAlone(String text) {
        assertThat(FactGuard.violations(text, CONTEXT)).isEmpty();
    }

    @Test
    void withoutFactsThereIsNothingToStateSoAnySumIsBlocked() {
        // A call with no context has no figure the agent is entitled to name.
        CallContext empty = new CallContext(null, null, null, null, null, null);
        assertThat(FactGuard.violations("Qarzingiz 900000 so'm.", empty))
                .containsExactly("900000");
        assertThat(FactGuard.violations("Ertaga to'laysizmi?", empty)).isEmpty();
    }

    @Test
    void handlesBlankAndNullText() {
        assertThat(FactGuard.violations(null, CONTEXT)).isEmpty();
        assertThat(FactGuard.violations("   ", CONTEXT)).isEmpty();
    }

    @Test
    void acceptsTheAmountWithADecimalTail() {
        CallContext withTiyin = new CallContext("A", new BigDecimal("1500000.50"), "so'm",
                null, null, null);
        assertThat(FactGuard.violations("Qarzingiz 1500000.50 so'm.", withTiyin)).isEmpty();
    }
}
