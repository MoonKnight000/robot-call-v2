package uz.murodjon.uysotvoice.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The guard sits between the model and the phone line, so both of its failure modes are
 * expensive: letting a wrong sum through means a caller is told they owe money they do not,
 * and blocking a correct sentence means the agent goes silent mid-call. These cases pin
 * both edges.
 */
class FactGuardTest {

    private static final ScenarioDefinition SCENARIO = ScenarioFixtures.debtCollection();

    private static final CallContext CONTEXT = new CallContext(Map.of(
            "clientName", "Aziz Karimov",
            "debtAmount", new BigDecimal("1500000"),
            "currency", "so'm",
            "dueDate", LocalDate.of(2026, 7, 1),
            "contractNumber", "UY-2026-00123"
    ), "goal");

    @ParameterizedTest
    @ValueSource(strings = {
            "Qarzingiz 1500000 so'm.",
            "Qarzingiz 1 500 000 so'm.",       // spaces are how a model groups digits
            "Qarzingiz 1.500.000 so'm.",       // Uzbek/Russian grouping
            "Qarzingiz 1,500,000 so'm.",       // English grouping
    })
    void acceptsTheDebtAmountHoweverItIsWritten(String text) {
        assertThat(FactGuard.violations(text, SCENARIO, CONTEXT)).isEmpty();
    }

    @Test
    void blocksAnAmountThatIsNotInTheFacts() {
        assertThat(FactGuard.violations("Qarzingiz 15000000 so'm.", SCENARIO, CONTEXT))
                .containsExactly("15000000");
    }

    @Test
    void blocksADiscountItInventedAlongsideTheRealFigure() {
        // The prompt forbids offering a discount; this is the check that the caller never
        // hears one anyway.
        assertThat(FactGuard.violations(
                "Qarzingiz 1500000 so'm, lekin 1200000 to'lasangiz ham bo'ladi.", SCENARIO, CONTEXT))
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
        assertThat(FactGuard.violations(text, SCENARIO, CONTEXT)).isEmpty();
    }

    @Test
    void withoutFactsThereIsNothingToStateSoAnySumIsBlocked() {
        // A call with no context has no figure the agent is entitled to name.
        CallContext empty = new CallContext(Map.of(), null);
        assertThat(FactGuard.violations("Qarzingiz 900000 so'm.", SCENARIO, empty))
                .containsExactly("900000");
        assertThat(FactGuard.violations("Ertaga to'laysizmi?", SCENARIO, empty)).isEmpty();
    }

    @Test
    void handlesBlankAndNullText() {
        assertThat(FactGuard.violations(null, SCENARIO, CONTEXT)).isEmpty();
        assertThat(FactGuard.violations("   ", SCENARIO, CONTEXT)).isEmpty();
    }

    @Test
    void acceptsTheAmountWithADecimalTail() {
        CallContext withTiyin = new CallContext(Map.of(
                "clientName", "A", "debtAmount", new BigDecimal("1500000.50")), null);
        assertThat(FactGuard.violations("Qarzingiz 1500000.50 so'm.", SCENARIO, withTiyin)).isEmpty();
    }

    // --- spelled-out sums (realtime calls transcribe speech, not digits) ---

    @ParameterizedTest
    @ValueSource(strings = {
            "Qarzingiz bir million besh yuz ming so'm.",
            "Qarzingiz BIR MILLION BESH YUZ MING so'm.",   // transcript casing varies
            "Qarzingiz bir million besh yuz ming so‘m.",   // typographic apostrophe
    })
    void acceptsTheDebtAmountSpelledOut(String text) {
        assertThat(FactGuard.violations(text, SCENARIO, CONTEXT)).isEmpty();
    }

    @Test
    void blocksASpelledOutAmountThatIsNotInTheFacts() {
        assertThat(FactGuard.violations("Qarzingiz besh million so'm.", SCENARIO, CONTEXT))
                .containsExactly("besh million");
    }

    @Test
    void readsNumberWordsWrittenWithATypographicApostrophe() {
        // A transcriber may return o‘n rather than o'n; the guard must still see fifteen
        // thousand there, or the apostrophe alone becomes a way past it.
        assertThat(FactGuard.violations("Yana o‘n besh ming so'm qo'shiladi.", SCENARIO, CONTEXT))
                .containsExactly("o‘n besh ming");
    }

    @Test
    void blocksASpelledOutDiscountAlongsideTheRealFigure() {
        assertThat(FactGuard.violations(
                "Qarzingiz 1500000 so'm, lekin bir million to'lasangiz ham bo'ladi.", SCENARIO, CONTEXT))
                .containsExactly("bir million");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ming rahmat, yaxshi kun!",               // politeness, not a claim about 1000
            "Bir necha kun ichida to'lang.",          // "bir" as an article, not a count
            "O'n besh kun muhlat beramiz.",           // conversational scale
            "Ikki ming yigirma oltinchi yilda.",      // a year, same as the digit form
            "Bir-ikki kun kutamiz.",
    })
    void leavesOrdinarySpokenUzbekAlone(String text) {
        assertThat(FactGuard.violations(text, SCENARIO, CONTEXT)).isEmpty();
    }

    @Test
    void readsBackAContractNumberSpokenDigitByDigit() {
        // A reference number is read one digit at a time. "nol" ends the run, so the year
        // in the middle of the contract number is read as a year and the digits after it
        // add up to 6 — neither is a money claim, and crucially they are not added
        // together into one.
        assertThat(FactGuard.violations(
                "Shartnoma raqami U Y ikki ming yigirma olti nol nol bir ikki uch.", SCENARIO, CONTEXT))
                .isEmpty();
    }

    @Test
    void doesNotMergeAnAmountWithTheDigitsReadOutAfterIt() {
        // Without "nol" breaking the run these would add up to 1500001 — a figure nobody
        // said, blocking a sentence in which every stated number was correct.
        assertThat(FactGuard.violations(
                "Bir million besh yuz ming nol nol bir.", SCENARIO, CONTEXT)).isEmpty();
    }
}
