package uz.murodjon.robotcallv2.aiagent.application.dto;

import jakarta.validation.Valid;
import uz.murodjon.robotcallv2.aiagent.domain.entity.WorkflowFunction;

import java.util.List;

/** The workflow's pre-call slot: the functions that run before the agent speaks. */
public record AgentPreCallFunctionsDto(
        @Valid List<WorkflowFunction> functions
) {
    public List<WorkflowFunction> functionsOrEmpty() {
        return functions != null ? functions : List.of();
    }
}
