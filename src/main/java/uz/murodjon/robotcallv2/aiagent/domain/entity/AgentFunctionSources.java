package uz.murodjon.robotcallv2.aiagent.domain.entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an agent that never saved a function list has outside its own row: the tools attached to it, the
 * integration tools its company has on, and whether the platform has a global operator. Together with the
 * agent's older fields it is everything its calls were offering, so the default list can say the same.
 *
 * @param attachedTools    the attached tools' ids with their model-facing names, in attachment order
 * @param integrationTools the company's enabled integration tools
 * @param globalOperator   whether a platform-wide operator exists to hand a call to
 */
public record AgentFunctionSources(Map<Long, String> attachedTools, List<AgentIntegrationTool> integrationTools, boolean globalOperator) {

    public static final AgentFunctionSources NONE = new AgentFunctionSources(Map.of(), List.of(), false);

    public AgentFunctionSources {
        attachedTools = attachedTools == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attachedTools));
        integrationTools = integrationTools == null ? List.of() : List.copyOf(integrationTools);
    }
}
