package uz.murodjon.uysotvoice.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uz.murodjon.uysotvoice.shared.dialog.DialogState;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.dialog.ReasonCode;

import java.math.BigDecimal;
import java.time.LocalDate;

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
        CallContext context = new CallContext("Aziz Karimov", new BigDecimal("1500000"), "so'm",
                LocalDate.of(2026, 7, 1), "UY-2026-00123", "goal");
        // No RTP endpoint: none of these tools touch audio.
        session = new DialogSession("chan-1", "uz-UZ", null, context, null, null, null, 42L, null);
        tools = new DialogTools(session);
    }

    @Test
    void rejectsAPromiseInThePast() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        String result = tools.recordPaymentPromise(yesterday, null, null);

        assertThat(result).startsWith("XATO:");
        // The usual cause is a wrong year, so today's date has to be in the reply —
        // without it the model has nothing to correct against and re-sends the same date.
        assertThat(result).contains(LocalDate.now().toString());
        assertThat(session.promisedDate()).isNull();
        assertThat(session.disposition()).isNull();
    }

    @Test
    void acceptsAPromiseFromToday() {
        LocalDate today = LocalDate.now();

        tools.recordPaymentPromise(today, new BigDecimal("500000"), "yarim to'lov");

        assertThat(session.promisedDate()).isEqualTo(today);
        assertThat(session.promisedAmount()).isEqualByComparingTo("500000");
        assertThat(session.disposition()).isEqualTo(Disposition.PROMISE_TO_PAY);
    }

    @Test
    void optOutEndsTheCallAndKeepsTheReason() {
        // §11.4: the reason has to survive until teardown writes the phone-level entry.
        tools.recordDoNotCall("mijoz boshqa qo'ng'iroq qilinmasligini so'radi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.DO_NOT_CALL);
        assertThat(session.doNotCallReason()).contains("qo'ng'iroq");
    }

    @Test
    void refusalRecordsTheReasonCode() {
        tools.recordRefusalReason(ReasonCode.JOB_LOSS, "ishdan bo'shadi");

        assertThat(session.reasonCode()).isEqualTo(ReasonCode.JOB_LOSS);
        assertThat(session.disposition()).isEqualTo(Disposition.REFUSED);
        assertThat(session.isEnded()).isFalse(); // the agent still has to close the call
    }

    @Test
    void transferEndsTheCallAsTransferred() {
        tools.requestHumanTransfer("mijoz operator so'radi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.TRANSFERRED);
    }

    @Test
    void wrongPersonEndsTheCall() {
        tools.recordWrongPerson("boshqa odam ko'tardi");

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.WRONG_NUMBER);
    }

    @Test
    void transitionMovesTheFsm() {
        tools.transitionTo(DialogState.IDENTITY_CHECK);

        assertThat(session.state()).isEqualTo(DialogState.IDENTITY_CHECK);
    }

    @Test
    void endCallKeepsTheDispositionItWasGiven() {
        tools.endCall(Disposition.HUNG_UP);

        assertThat(session.isEnded()).isTrue();
        assertThat(session.disposition()).isEqualTo(Disposition.HUNG_UP);
    }
}
