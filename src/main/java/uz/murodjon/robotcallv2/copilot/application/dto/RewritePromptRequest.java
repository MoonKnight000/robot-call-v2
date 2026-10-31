package uz.murodjon.robotcallv2.copilot.application.dto;

import jakarta.validation.constraints.Size;

/**
 * At least one of the two is set: a prompt alone is improved, an instruction alone is written
 * into a new prompt, both together rewrite the prompt the way the instruction says.
 *
 * @param prompt      the current prompt text; may be blank
 * @param instruction what to change or write, in the user's words; may be blank
 */
public record RewritePromptRequest(
        @Size(max = 20000) String prompt,
        @Size(max = 2000) String instruction
) {
}
