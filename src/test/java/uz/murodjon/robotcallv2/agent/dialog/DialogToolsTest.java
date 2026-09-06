package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These tools are the only way the model can change a call's outcome, and the
 * guardrails in them (§4.4) are the last line before a wrong promise reaches the CRM.
 */
class DialogToolsTest {

    private DialogSession session;
    private DialogTools tools;

    @BeforeEach
    void setUp() {
        CallContext context = new CallContext(Map.of(
                "clientName", "Aziz Karimov",
                "debtAmount", new BigDecimal("1500000"),
                "currency", "so'm",
                "dueDate", LocalDate.of(2026, 7, 1),
                "contractNumber", "UY-2026-00123"
        ), "goal");
        // No RTP endpoint: none of these tools touch audio.
        session = new DialogSession("chan-1", "uz-UZ", null, context, ScenarioFixtures.debtCollection(),
                null, null, null, 42L, 1L, null, true, "Uysot", null, null, EffectiveVoiceSettings.NONE);
        tools = new DialogTools(session);
    }

    @Test
    void dropsUnspeakableReplyInTransitionTo() {
        session.setState("DEBT_NOTICE");

        tools.transitionTo("dynamic_thought_or_fallback", "REASON_INQUIRY");

        assertThat(session.state()).isEqualTo("REASON_INQUIRY");
        assertThat(session.toolReplies()).isNull();
    }

    @Test
    void keepsValidReplyInTransitionTo() {
        session.setState("DEBT_NOTICE");

        tools.transitionTo("Tushunarli. Sabab nimada?", "REASON_INQUIRY");

        assertThat(session.state()).isEqualTo("REASON_INQUIRY");
        assertThat(session.toolReplies()).isEqualTo("Tushunarli. Sabab nimada?");
    }

    @Test
    void rejectsAPromiseInThePast() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        String result = tools.recordPaymentPromise("Yaxshi, yozib qo'ydim.", yesterday, null, null);

        assertThat(result).startsWith("XATO:");
        // The line confirmed a promise the guardrail refused — it must not be spoken.
        assertThat(session.toolReplies()).isNull();
        // The usual cause is a wrong year, so today's date has to be in the reply —
        // without it the model has nothing to correct against and re-sends the same date.
        assertThat(result).contains(LocalDate.now().toString());
        assertThat(session.outcome()).doesNotContainKey("promisedDate");
        assertThat(session.disposition()).isNull();
    }

    @Test
    void acceptsAPromiseFromToday() {
        LocalDate today = LocalDate.now();

        tools.recordPaymentPromise("Kelishdik, belgilab qo'ydim.", today, new BigDecimal("500000"), "yarim to'lov");

        // What the caller hears on a tool-only turn, without a second LLM round trip.
        assertThat(session.toolReplies()).isEqualTo("Kelishdik, belgilab qo'ydim.");
        assertThat(session.outcome().get("promisedDate")).isEqualTo(today);
        assertThat((BigDecimal) session.outcome().get("promisedAmount")).isEqualByComparingTo("500000");
        assertThat(session.disposition()).isEqualTo(Disposition.PROMISE_TO_PAY);
    }

    @Test
    void optOutEndsTheCallAndKeepsTheReason() {
        // §11.4: the reason has to survive until teardown writes the phone-level entry.
        tools.recordDoNotCall("Uzr so'rayman, boshqa bezovta qilmaymiz.",
                "mijoz boshqa qo'ng'iroq qilinmasligini so'radi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.DO_NOT_CALL);
        assertThat(session.doNotCallReason()).contains("qo'ng'iroq");
    }

    @Test
    void refusalRecordsTheReasonCode() {
        tools.recordRefusalReason("Tushundim, holatingizni yozib qo'ydim.", "JOB_LOSS", "ishdan bo'shadi");

        assertThat(session.outcome().get("reasonCode")).isEqualTo("JOB_LOSS");
        assertThat(session.disposition()).isEqualTo(Disposition.REFUSED);
        assertThat(session.isEnded()).isFalse(); // the agent still has to close the call
    }

    @Test
    void transferEndsTheCallAsTransferred() {
        tools.requestHumanTransfer("Bir daqiqa, operatorga ulayman.", "mijoz operator so'radi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.TRANSFERRED);
    }

    @Test
    void wrongPersonEndsTheCall() {
        tools.recordWrongPerson("Uzr, bezovta qildim.", "boshqa odam ko'tardi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.WRONG_NUMBER);
    }

    @Test
    void transitionMovesTheFsm() {
        tools.transitionTo("Murodjon sizmi?", "IDENTITY_CHECK");

        assertThat(session.state()).isEqualTo("IDENTITY_CHECK");
        assertThat(session.toolReplies()).isEqualTo("Murodjon sizmi?");
    }

    @Test
    void refusesATransitionTheScenarioDoesNotAllow() {
        // GREETING leads to IDENTITY_CHECK: jumping past it skips verifying who answered.
        String result = tools.transitionTo("Sizning qarzingiz bor.", "DEBT_NOTICE");

        assertThat(result).startsWith("XATO:");
        // The model has to be told where it may go, or it re-sends the same stage.
        assertThat(result).contains("IDENTITY_CHECK");
        assertThat(session.state()).isEqualTo("GREETING");
        assertThat(session.toolReplies()).isNull();
    }

    @Test
    void endCallKeepsTheDispositionItWasGiven() {
        tools.endCall("Xayr, kuningiz xayrli o'tsin!", Disposition.HUNG_UP);

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.HUNG_UP);
    }
}
