package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.List;

/** Settings of an EXTRACT_VARIABLE function: the variables its one tool records. */
public record AgentFunctionExtract(List<AgentFunctionVariable> variables) {

    public AgentFunctionExtract {
        variables = variables == null ? List.of() : List.copyOf(variables);
    }
}
