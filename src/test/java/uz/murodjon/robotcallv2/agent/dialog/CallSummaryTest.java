package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.shared.dialog.Sentiment;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a zero-PII agent's call is allowed to keep of its summary. */
class CallSummaryTest {

    private static final CallSummary FULL = new CallSummary(
            "Ali Valiyev 5-oktyabrgacha to'lashga va'da berdi, manzili Chilonzor 12",
            Map.of(
                    "reasonCode", "PROMISED",
                    "promisedDate", "2026-10-05",
                    "promisedAmount", 1_500_000,
                    "client_name", "Ali Valiyev",
                    "evaluation", Map.of("polite", Map.of("verdict", "PASS", "reason", "Ali akani hurmat bilan chaqirdi"))),
            Sentiment.POSITIVE, true, "Ali akaga 5-oktyabrda qo'ng'iroq", "2026-10-05T10:00:00", 90, 80);

    @Test
    void dropsTheWordsTheModelWroteFromTheConversation() {
        CallSummary stripped = FULL.stripPersonalData(null);

        assertThat(stripped.summary()).isEmpty();
        assertThat(stripped.followUpNote()).isNull();
        assertThat(stripped.outcome()).doesNotContainKey("client_name");
    }

    @Test
    void keepsTheStructuredOutcomeAndTheVerdictsWithoutTheirReasons() {
        CallSummary stripped = FULL.stripPersonalData(null);

        assertThat(stripped.outcome()).containsEntry("reasonCode", "PROMISED")
                .containsEntry("promisedDate", "2026-10-05")
                .containsEntry("promisedAmount", 1_500_000)
                .containsEntry("evaluation", Map.of("polite", Map.of("verdict", "PASS")));
        assertThat(stripped.sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(stripped.needsFollowUp()).isTrue();
        assertThat(stripped.callbackAt()).isEqualTo("2026-10-05T10:00:00");
        assertThat(stripped.qaScore()).isEqualTo(90);
        assertThat(stripped.commitmentScore()).isEqualTo(80);
    }

    @Test
    void keepsTheScenariosGoalFieldWithTheRestOfTheWordsGone() {
        // A clinic turns zero-PII on to keep the complaint out of its records, not to lose the
        // appointment it made (UNIVERSAL-OPERATOR-PLAN E.3).
        CallSummary booked = new CallSummary("Dilnoza boshi og'rigani uchun terapevtga yozildi",
                Map.of("slotBooked", "4821", "patient_name", "Dilnoza"), Sentiment.NEUTRAL, false, null);

        CallSummary stripped = booked.stripPersonalData("slotBooked");

        assertThat(stripped.summary()).isEmpty();
        assertThat(stripped.outcome()).containsEntry("slotBooked", "4821").doesNotContainKey("patient_name");
    }

    @Test
    void anOutcomeWithNothingStructuredComesBackEmpty() {
        CallSummary stripped = new CallSummary("Ali", Map.of("client_name", "Ali"), null, false, null)
                .stripPersonalData(null);

        assertThat(stripped.outcome()).isEmpty();
    }
}
