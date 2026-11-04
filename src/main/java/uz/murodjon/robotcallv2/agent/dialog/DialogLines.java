package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentPhrases;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The lines this engine speaks from code rather than through the model — the opening
 * disclosure, the farewell a guardrail closes with, and the neutral line for a turn that
 * produced nothing sayable.
 *
 * <p>Kept together because they share one property: none of them may depend on the LLM
 * having cooperated. The disclosure is a legal requirement (§11.1), and the other two are
 * what stands between the caller and silence.
 *
 * <p>Every line but the disclosure is the agent's own wording ({@code AiAgent.phrases}) when
 * it has one and the call runs in the agent's language, the standard {@link DialogPhrases} line otherwise.
 */
final class DialogLines {

    private DialogLines() {
    }

    /** The §11.1 disclosure this call opens with ({@link DialogPolicy#resolveDisclosureLine}). */
    static String disclosure(DialogSession s) {
        return DialogPolicy.resolveDisclosureLine(s.scenario(), s.language(), s.companyName());
    }

    /**
     * Last resort when the LLM returns no text at all: the caller must hear something.
     * In the opening stage that has to be the opening line (nobody has spoken yet); later
     * on, the stage's own {@code fallbackLine} asks its question again, and a stage without
     * one asks the client to repeat — either keeps the conversation alive.
     */
    static String fallback(DialogSession s) {
        if (s.state().equals(s.scenario().findStartStage().id()) && !s.isDisclosureSpoken()) {
            return disclosure(s);
        }
        StageDef stage = s.scenario().findStage(s.state());
        if (stage != null && stage.fallbackLine() != null && !stage.fallbackLine().isBlank()) {
            return PromptTemplateEngine.render(stage.fallbackLine().trim(), StageEntry.variables(s));
        }
        return own(s, AiAgentPhrases::didNotCatch, DialogPhrases.didNotCatch(s.language()));
    }

    /** Asked when the line has gone quiet. */
    static String stillThere(DialogSession s) {
        return own(s, AiAgentPhrases::stillThere, DialogPhrases.stillThere(s.language()));
    }

    /** Whether the agent worded the still-there check itself — then that wording is what is said. */
    static boolean hasOwnStillThere(DialogSession s) {
        AiAgentPhrases own = ownPhrases(s);
        return own != null && own.stillThere() != null;
    }

    /** The last check on a quiet line, which says the call will end if it stays quiet. */
    static String stillThereLastCall(DialogSession s) {
        return own(s, AiAgentPhrases::stillThereLastCall, DialogPhrases.stillThereLastCall(s.language()));
    }

    /** The reply to a caller's closing "thank you, goodbye". */
    static String gratitudeReply(DialogSession s) {
        return own(s, AiAgentPhrases::gratitudeReply, DialogPhrases.gratitudeReply(s.language()));
    }

    /** The reply to a caller who asks never to be called again. */
    static String doNotCallReply(DialogSession s) {
        return own(s, AiAgentPhrases::doNotCallReply, DialogPhrases.doNotCallReply(s.language()));
    }

    /** Holding lines over a long wait: the agent's own pool when it has one. */
    static List<String> thinking(DialogSession s) {
        AiAgentPhrases own = ownPhrases(s);
        return own == null || own.thinking().isEmpty() ? DialogPhrases.thinking(s.language()) : own.thinking();
    }

    /** Listening noises over a long answer: the agent's own pool when it has one. */
    static List<String> backchannels(DialogSession s) {
        AiAgentPhrases own = ownPhrases(s);
        return own == null || own.backchannels().isEmpty() ? DialogPhrases.backchannels(s.language()) : own.backchannels();
    }

    /**
     * Said when the call is handed to a person: the agent's own {@code transferMessage} when
     * it has one, the standard line otherwise — the same words whether the model asked for
     * the transfer or code escalated on its own.
     */
    static String transferring(DialogSession s) {
        String own = s.agent() != null ? s.agent().callBehaviour().transferMessage() : null;
        return own != null && !own.isBlank() ? own.trim() : DialogPhrases.transferring(s.language());
    }

    /** Closing line when a guardrail ends the call, in the caller's language. */
    static String farewell(DialogSession s) {
        return own(s, AiAgentPhrases::farewell, DialogPhrases.farewell(s.language()));
    }

    private static String own(DialogSession s, Function<AiAgentPhrases, String> pick, String standard) {
        AiAgentPhrases phrases = ownPhrases(s);
        String line = phrases != null ? pick.apply(phrases) : null;
        return line != null ? line : standard;
    }

    /**
     * The agent's own lines, or null when this call cannot use them: they are written in the
     * agent's language, and a call that switched to another of its languages must not hear them.
     */
    private static AiAgentPhrases ownPhrases(DialogSession s) {
        AiAgent agent = s.agent();
        return agent != null && sameLanguage(agent.language(), s.language()) ? agent.phrases() : null;
    }

    /** Same language by its primary subtag: "uz-UZ" and "uz" are one language, "uz" and "ru" are not. */
    private static boolean sameLanguage(String a, String b) {
        return a != null && b != null && primary(a).equals(primary(b));
    }

    private static String primary(String language) {
        int dash = language.indexOf('-');
        return (dash < 0 ? language : language.substring(0, dash)).toLowerCase(Locale.ROOT);
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
