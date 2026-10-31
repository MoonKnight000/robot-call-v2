package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.aiagent.AiAgentFixtures;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentCallBehaviour;
import uz.murodjon.robotcallv2.shared.dialog.InterruptionSensitivity;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SMS tools reach a real gateway, so what the model can make them send is bounded here:
 * one SMS a call, to the call's own number, of a length a phone shows, only through the
 * agent's template — and a send that did not happen is never reported to the model as one
 * that did.
 */
class DialogToolsSmsGuardTest {

    /** A template that is nothing but the slot, so the guards see the model's text as sent. */
    private static final String SLOT_ONLY = "{{text}}";

    private final List<String> sent = new ArrayList<>();

    @Test
    void refusesToSendWithoutATemplate() {
        DialogTools tools = new DialogTools(session(Map.of(), sent::add));

        String result = tools.sendMidCallSms("SMS yubordim.", "Karta raqami: 8600 1234 5678 9012");

        assertThat(result).startsWith("ERROR:");
        assertThat(sent).isEmpty();
    }

    @Test
    void sendsOnlyOneSmsPerCall() {
        DialogTools tools = new DialogTools(sessionWithTemplate(SLOT_ONLY, Map.of(), sent::add));

        String first = tools.sendMidCallSms(null, "Karta raqami: 8600 1234 5678 9012");
        String second = tools.sendMidCallSms("Yana yubordim.", "Yana bir SMS");

        assertThat(first).doesNotStartWith("ERROR:");
        assertThat(second).startsWith("ERROR:");
        assertThat(sent).containsExactly("Karta raqami: 8600 1234 5678 9012");
    }

    @Test
    void aTemplateCanCarryTheCallsOwnPaymentLink() {
        DialogTools tools = new DialogTools(sessionWithTemplate("To'lov havolasi: {{paymentLink}}",
                Map.of("paymentLink", "https://pay.example.uz/abc123"), sent::add));

        String result = tools.sendMidCallSms(null, null);

        assertThat(result).doesNotStartWith("ERROR:");
        // The model decides when, never what: the text is the template and the link is the call's own.
        assertThat(sent).containsExactly("To'lov havolasi: https://pay.example.uz/abc123");
    }

    @Test
    void refusesAnOverlongTextWithoutSpendingTheCallsSms() {
        DialogTools tools = new DialogTools(sessionWithTemplate(SLOT_ONLY, Map.of(), sent::add));

        String overlong = tools.sendMidCallSms(null, "a".repeat(DialogTools.MAX_SMS_CHARS + 1));
        String shortOne = tools.sendMidCallSms(null, "Qisqa matn");

        assertThat(overlong).startsWith("ERROR:").contains(String.valueOf(DialogTools.MAX_SMS_CHARS));
        assertThat(shortOne).doesNotStartWith("ERROR:");
        assertThat(sent).containsExactly("Qisqa matn");
    }

    @Test
    void reportsAnSmsTheGatewayRefusedAsNotSent() {
        DialogSession session = sessionWithTemplate(SLOT_ONLY, Map.of(), text -> false);
        DialogTools tools = new DialogTools(session);

        String result = tools.sendMidCallSms("SMS yubordim.", "Salom");

        assertThat(result).startsWith("ERROR:");
        // The line claims an SMS that never went — it must not be spoken.
        assertThat(session.toolReplies()).isNull();
        assertThat(session.outcome()).doesNotContainKey("midCallSmsSent");
    }

    @Test
    void reportsNotSentWhenTheCallHasNoSmsSender() {
        DialogSession session = sessionWithTemplate(SLOT_ONLY, Map.of(), null);

        String result = new DialogTools(session).sendMidCallSms(null, "Salom");

        assertThat(result).startsWith("ERROR:");
    }

    @Test
    void fillsTheAgentsSmsTemplateInsteadOfSendingTheModelsText() {
        DialogSession session = sessionWithTemplate(
                "Hurmatli {{clientName | mijoz}}, to'lov havolasi: {{text}}", Map.of("clientName", "Murodjon"),
                sent::add);

        String result = new DialogTools(session).sendMidCallSms(null, "https://pay.example.uz/abc123");

        assertThat(result).doesNotStartWith("ERROR:");
        assertThat(sent).containsExactly("Hurmatli Murodjon, to'lov havolasi: https://pay.example.uz/abc123");
        assertThat(session.outcome())
                .containsEntry("midCallSmsText", "Hurmatli Murodjon, to'lov havolasi: https://pay.example.uz/abc123");
    }

    @Test
    void refusesAnEmptyTemplateSlotWithoutSpendingTheCallsSms() {
        DialogTools tools = new DialogTools(sessionWithTemplate("To'lov havolasi: {{text}}", Map.of(), sent::add));

        String empty = tools.sendMidCallSms(null, " ");
        String filled = tools.sendMidCallSms(null, "https://pay.example.uz/abc123");

        assertThat(empty).startsWith("ERROR:");
        assertThat(filled).doesNotStartWith("ERROR:");
        assertThat(sent).containsExactly("To'lov havolasi: https://pay.example.uz/abc123");
    }

    @Test
    void sendsATemplateWithoutASlotAsItStands() {
        DialogTools tools = new DialogTools(
                sessionWithTemplate("Operatorimiz tez orada siz bilan bog'lanadi.", Map.of(), sent::add));

        tools.sendMidCallSms(null, "model yozgan boshqa matn");

        assertThat(sent).containsExactly("Operatorimiz tez orada siz bilan bog'lanadi.");
    }

    private static DialogSession session(Map<String, Object> facts, Predicate<String> smsSender) {
        DialogSession session = baseSession(facts).build();
        session.setMidCallSmsSender(smsSender);
        return session;
    }

    private static DialogSessionBuilder baseSession(Map<String, Object> facts) {
        return DialogSession.builder("chan-1", "uz-UZ")
                .context(new CallContext(facts, "goal"))
                .scenario(ScenarioFixtures.debtCollection())
                .callAttemptId(42L)
                .companyId(1L)
                .companyName("Uysot");
    }

    private static DialogSession sessionWithTemplate(String template, Map<String, Object> facts,
                                                     Predicate<String> smsSender) {
        AiAgent base = AiAgentFixtures.agent(3L, 1L, 10L, "uz-UZ", "nigora");
        AiAgentCallBehaviour behaviour = new AiAgentCallBehaviour(InterruptionSensitivity.MEDIUM, false,
                VoicemailAction.LEAVE_MESSAGE, null, true, template, null, null, null);
        AiAgent agent = base.withAdvanced(base.ambience(), behaviour, base.dataPolicy(), base.voice(),
                base.useRag(), base.initiationWebhook(), base.postCallWebhook());
        DialogSession session = baseSession(facts).agent(agent).build();
        session.setMidCallSmsSender(smsSender);
        return session;
    }
}
