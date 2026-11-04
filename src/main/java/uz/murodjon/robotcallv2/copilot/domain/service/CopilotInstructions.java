package uz.murodjon.robotcallv2.copilot.domain.service;

import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotPageContext;
import uz.murodjon.robotcallv2.shared.util.PromptText;

import java.time.LocalDate;

/**
 * What the copilot's model is told before every question. The rules that keep it honest live
 * here: facts only from tool answers, changes only to drafts, publishing left to a person.
 * The text lives in {@code resources/prompts/copilot.txt}.
 */
public final class CopilotInstructions {

    private static final String TEXT = PromptText.text("copilot.txt");
    private static final String PROMPT_REWRITE_TEXT = PromptText.text("prompt-rewrite.txt");

    /** What the prompt button's model answers when asked for something other than an agent prompt. */
    public static final String OUT_OF_SCOPE = "OUT_OF_SCOPE";

    private CopilotInstructions() {
    }

    /**
     * @param context the panel page the question came from; null or empty adds nothing
     */
    public static String forQuestion(LocalDate today, CopilotPageContext context) {
        String text = TEXT.formatted(today);
        if (context == null || context.isEmpty()) {
            return text;
        }
        StringBuilder page = new StringBuilder("\n\nCURRENT PAGE\nThe user is asking from the panel");
        if (context.page() != null) {
            page.append(" page \"").append(context.page()).append('"');
        }
        page.append(". What is open there:");
        appendId(page, "agent", context.agentId());
        appendId(page, "campaign", context.campaignId());
        appendId(page, "call", context.callId());
        appendId(page, "scenario", context.scenarioId());
        page.append("\n\"Shu agent\", \"bu kampaniya\", \"bu qo'ng'iroq\" (\"этот агент\", ...) mean these. ")
                .append("Read them with your tools before answering; a question clearly about something else ")
                .append("is about that instead.");
        return text + page;
    }

    /** For the prompt editors' AI button: rewrites or writes one prompt, no tools. */
    public static String forPromptRewrite() {
        return PROMPT_REWRITE_TEXT;
    }

    private static void appendId(StringBuilder page, String label, Long id) {
        if (id != null) {
            page.append("\n- ").append(label).append(" ID ").append(id);
        }
    }
}
