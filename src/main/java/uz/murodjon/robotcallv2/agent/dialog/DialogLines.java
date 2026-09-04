package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disclosure;

/**
 * The lines this engine speaks from code rather than through the model — the opening
 * disclosure, the farewell a guardrail closes with, and the neutral line for a turn that
 * produced nothing sayable.
 *
 * <p>Kept together because they share one property: none of them may depend on the LLM
 * having cooperated. The disclosure is a legal requirement (§11.1), and the other two are
 * what stands between the caller and silence.
 */
final class DialogLines {

    private DialogLines() {
    }

    /**
     * The §11.1 disclosure this call opens with, most specific wording first: the
     * scenario's own, then the calling company's, then the platform's. Each configured
     * text has to survive {@link Disclosure} to be used at all — that is what keeps the
     * notice from being worded away, whichever level tries it.
     */
    static String disclosure(DialogSession s) {
        String scenarioLine = Disclosure.resolve(
                s.scenario() != null ? s.scenario().disclosureText() : null, s.language(), s.companyName());
        if (scenarioLine != null) {
            return scenarioLine;
        }
        String companyLine = Disclosure.resolve(s.companyDisclosureText(), s.language(), s.companyName());
        return companyLine != null ? companyLine : DialogPhrases.disclosure(s.language(), s.companyName());
    }

    /**
     * Last resort when the LLM returns no text at all: the caller must hear something.
     * In the opening stage that has to be the opening line (nobody has spoken yet); later
     * on, asking the client to repeat keeps the conversation alive.
     */
    static String fallback(DialogSession s) {
        if (s.state().equals(s.scenario().stages().get(0).id()) && !s.isDisclosureSpoken()) {
            return disclosure(s);
        }
        if ("REASON_INQUIRY".equalsIgnoreCase(s.state())) {
            return s.language() != null && s.language().startsWith("ru")
                    ? "Понятно. Скажите, пожалуйста, по какой причине задерживается оплата?"
                    : "Tushunarli. To'lov kechikishining sababi nimada ekanligini ayta olasizmi?";
        }
        if ("PAYMENT_DATE".equalsIgnoreCase(s.state())) {
            return s.language() != null && s.language().startsWith("ru")
                    ? "Понятно. Назовите, пожалуйста, точную дату оплаты."
                    : "Tushunarli. Qaysi sanada to'lov qila olasiz?";
        }
        return DialogPhrases.didNotCatch(s.language());
    }

    /** Closing line when a guardrail ends the call, in the caller's language. */
    static String farewell(DialogSession s) {
        return DialogPhrases.farewell(s.language());
    }

    /**
     * The last question in what the agent said last, or {@code null} if it asked none —
     * a statement is not worth repeating into a silence, and the closing lines are
     * statements.
     */
    static String lastQuestion(String agentText) {
        if (agentText == null) {
            return null;
        }
        int end = agentText.lastIndexOf('?');
        if (end < 0) {
            return null;
        }
        int start = 0;
        for (int i = end - 1; i >= 0; i--) {
            char c = agentText.charAt(i);
            if (c == '.' || c == '!' || c == '?' || c == '\n') {
                start = i + 1;
                break;
            }
        }
        String question = agentText.substring(start, end + 1).trim();
        return question.isEmpty() ? null : question;
    }
}
