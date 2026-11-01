package uz.murodjon.robotcallv2.aiagent.domain.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * One function of an agent's workflow: a tool that runs around the conversation rather than in it —
 * before the agent speaks (pre-call), to bring in what the conversation should start from.
 *
 * <p>Functions of a slot form a dependency graph: those with no {@code dependsOn} start together,
 * and one that depends on others waits for them and reads what they produced.
 *
 * @param id        names the function inside its slot; what {@code dependsOn} refers to
 * @param toolName  the tool to run: one attached to the agent, or one of the integration's tools
 * @param inputs    the tool's arguments by name; each value is written text where {@code {{variable}}}
 *                  is filled from the call's variables and from what earlier functions produced. A
 *                  function with an input whose variable has no value is skipped rather than sent
 *                  half-formed
 * @param outputs   variables to produce, by name: each is a path into the tool's answer
 *                  ({@code $.data[0].id}, {@code $.items[*]{number,price}}), read from the answer as it
 *                  came — before anything is cut for the model
 * @param dependsOn ids of the functions this one waits for; it runs only if they all succeeded
 * @param enabled   whether the function runs
 * @param code      instead of a tool: a JavaScript function body that reads {@code vars} (the call's variables and
 *                  what earlier functions produced) and returns an object whose fields become variables.
 *                  Runs in the {@code StageScriptRunner} sandbox; {@code toolName}, {@code inputs} and
 *                  {@code outputs} are then unused
 */
public record WorkflowFunction(
        @NotBlank @Size(max = 64) String id,
        @Size(max = 100) String toolName,
        Map<String, String> inputs,
        Map<String, String> outputs,
        List<String> dependsOn,
        boolean enabled,
        @Size(max = 10_000) String code
) {
    public WorkflowFunction {
        inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
        toolName = toolName == null || toolName.isBlank() ? null : toolName.trim();
        code = code == null || code.isBlank() ? null : code;
    }
}
