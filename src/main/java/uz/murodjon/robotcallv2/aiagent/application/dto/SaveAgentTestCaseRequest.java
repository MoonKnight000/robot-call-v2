package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * A test case, created or replaced as a whole (NEXT-PLAN A.2).
 *
 * @param personaPrompt     instructions to the model that plays the caller: who they are,
 *                          what they want, how they react
 * @param facts             the call's facts, keyed by the scenario's {@code factSchema} names
 * @param language          the caller's language; null uses the agent's
 * @param expectDisposition how the call must end ({@code PROMISE_TO_PAY}, {@code REFUSED}, …)
 * @param mustSay           phrases the agent has to say (case-insensitive)
 * @param mustNotSay        phrases the agent must never say
 * @param maxTurns          turns before the call is cut off; null means 12
 */
public record SaveAgentTestCaseRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 4000) String personaPrompt,
        Map<String, Object> facts,
        @Size(max = 10) String language,
        @Size(max = 40) String expectDisposition,
        @Size(max = 100) String expectStage,
        @Size(max = 20) List<String> mustSay,
        @Size(max = 20) List<String> mustNotSay,
        @Min(1) @Max(30) Integer maxTurns
) {
}
