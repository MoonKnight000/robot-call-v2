package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import uz.murodjon.robotcallv2.scenario.domain.entity.ToolDef;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The outcome tools a scenario declares (promise, refusal…) replaced the hardcoded ones in
 * {@link DialogTools}: their rules — no past promise, no half-recorded call — now live in the
 * factory, and so does the disposition a call of them gives.
 */
class ScenarioToolCallbackFactoryTest {

    private DialogSession session;
    private ToolDef promise;
    private ToolDef refusal;

    @BeforeEach
    void setUp() {
        session = DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(Map.of("clientName", "Aziz Karimov"), "goal"))
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(42L)
                .companyId(1L)
                .companyName("Uysot")
                .build();
        promise = ScenarioFixtures.debtCollection().tools().stream()
                .filter(tool -> tool.name().equals("recordPaymentPromise")).findFirst().orElseThrow();
        refusal = ScenarioFixtures.debtCollection().tools().stream()
                .filter(tool -> tool.name().equals("recordRefusalReason")).findFirst().orElseThrow();
    }

    private String call(ToolDef toolDef, String json) {
        ToolCallback callback = ScenarioToolCallbackFactory.build(toolDef, session);
        return callback.call(json);
    }

    @Test
    void rejectsAPromiseInThePast() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        String result = call(promise, "{\"reply\":\"Yaxshi, yozib qo'ydim.\",\"promisedDate\":\"" + yesterday + "\"}");

        assertThat(result).startsWith("ERROR:");
        // The line confirmed a promise the guardrail refused — it must not be spoken.
        assertThat(session.toolReplies()).isNull();
        // The usual cause is a wrong year, so today's date has to be in the reply —
        // without it the model has nothing to correct against and re-sends the same date.
        assertThat(result).contains(LocalDate.now().toString());
        assertThat(session.outcome()).doesNotContainKey("promisedDate");
        assertThat(session.disposition()).isNull();
    }

    @Test
    void aRefusedDateLeavesNoHalfRecordedAmountBehind() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        call(promise, "{\"promisedDate\":\"" + yesterday + "\",\"promisedAmount\":500000}");

        assertThat(session.outcome()).doesNotContainKey("promisedAmount");
    }

    @Test
    void acceptsAPromiseFromToday() {
        LocalDate today = LocalDate.now();

        String result = call(promise, "{\"reply\":\"Kelishdik, belgilab qo'ydim.\",\"promisedDate\":\"" + today
                + "\",\"promisedAmount\":500000,\"paymentChannel\":\"CLICK\",\"note\":\"yarim to'lov\"}");

        assertThat(result).doesNotStartWith("ERROR:");
        // What the caller hears on a tool-only turn, without a second LLM round trip.
        assertThat(session.toolReplies()).isEqualTo("Kelishdik, belgilab qo'ydim.");
        assertThat(session.outcome().get("promisedDate")).isEqualTo(today);
        assertThat((BigDecimal) session.outcome().get("promisedAmount")).isEqualByComparingTo("500000");
        assertThat(session.outcome().get("paymentChannel")).isEqualTo("CLICK");
        assertThat(session.disposition()).isEqualTo(Disposition.PROMISE_TO_PAY);
    }

    @Test
    void aMissingRequiredDateIsRefused() {
        String result = call(promise, "{\"promisedAmount\":500000}");

        assertThat(result).startsWith("ERROR:").contains("promisedDate");
        assertThat(session.disposition()).isNull();
    }

    @Test
    void refusalRecordsTheReasonCodeAndTheDisposition() {
        call(refusal, "{\"reply\":\"Tushundim, holatingizni yozib qo'ydim.\",\"reasonCode\":\"JOB_LOSS\"}");

        assertThat(session.outcome().get("reasonCode")).isEqualTo("JOB_LOSS");
        assertThat(session.disposition()).isEqualTo(Disposition.REFUSED);
        assertThat(session.isEnded()).isFalse(); // the agent still has to close the call
    }

    @Test
    void aToolWithNoDispositionOnlyRecords() {
        ToolDef note = new ToolDef("recordInterest", "Mijoz qiziqishini yozadi",
                java.util.List.of(new uz.murodjon.robotcallv2.scenario.domain.entity.ToolParamDef(
                        "interest", "string", true, null)));

        call(note, "{\"interest\":\"3 xonali\"}");

        assertThat(session.outcome().get("interest")).isEqualTo("3 xonali");
        assertThat(session.disposition()).isNull();
    }

    @Test
    void anUnknownDispositionIsIgnoredRatherThanFailingTheCall() {
        ToolDef odd = new ToolDef("recordThing", "x", java.util.List.of(), null, "NOT_A_DISPOSITION");

        String result = call(odd, "{}");

        assertThat(result).doesNotStartWith("ERROR:");
        assertThat(session.disposition()).isNull();
    }
}
