package uz.murodjon.robotcallv2.copilot.domain.service;

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

    private CopilotInstructions() {
    }

    public static String forDate(LocalDate today) {
        return TEXT.formatted(today);
    }

    /** For the prompt editors' AI button: rewrites or writes one prompt, no tools. */
    public static String forPromptRewrite() {
        return PROMPT_REWRITE_TEXT;
    }
}
