package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmTool;

import java.util.HashSet;
import java.util.Set;

/**
 * Which of the company's enabled integration tools an agent is offered. One rule for both pipelines and for
 * both kinds of tool: the built-in CRM tools ({@link CrmToolCallbacks}) and the integration's HTTP tools
 * ({@link IntegrationHttpToolFactory}).
 *
 * <p>An agent that saved a function list is offered exactly its INTEGRATION functions ({@link
 * AgentFunctionPlan#findAllowedIntegrationNames}); any other agent is offered everything the company enabled
 * except what it switched off ({@code AiAgent#disabledIntegrationTools}).
 *
 * <p>An entry is a catalog key of a built-in tool ({@code CREATE_LEAD}), or the name the model
 * calls a tool by ({@code createLead}, or an HTTP tool's name). A name the catalog no longer has
 * matches nothing and is ignored.
 */
final class IntegrationToolFilter {

    private IntegrationToolFilter() {
    }

    /** {@code names} (model-facing) without the ones this call's agent is not offered. */
    static Set<String> removeDisabled(DialogOutcomeSink session, Set<String> names) {
        Set<String> allowed = AgentFunctionPlan.findAllowedIntegrationNames(session.agent());
        Set<String> disabled = allowed == null ? findDisabledNames(session.agent()) : Set.of();
        if (allowed == null && disabled.isEmpty()) {
            return names;
        }
        Set<String> remaining = new HashSet<>(names);
        if (allowed != null) {
            remaining.retainAll(allowed);
        } else {
            remaining.removeAll(disabled);
        }
        return remaining;
    }

    /**
     * Whether the agent is offered a tool known by any of {@code modelNames} — an HTTP tool answers to its raw and
     * its sanitised name. With a function list, one of them has to be named there; without, none may be switched off.
     */
    static boolean isOffered(DialogOutcomeSink session, String... modelNames) {
        Set<String> allowed = AgentFunctionPlan.findAllowedIntegrationNames(session.agent());
        Set<String> disabled = allowed == null ? findDisabledNames(session.agent()) : Set.of();
        for (String modelName : modelNames) {
            if (allowed != null && allowed.contains(modelName)) {
                return true;
            }
            if (allowed == null && disabled.contains(modelName)) {
                return false;
            }
        }
        return allowed == null;
    }

    private static Set<String> findDisabledNames(AiAgent agent) {
        if (agent == null || agent.disabledIntegrationTools().isEmpty()) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        for (String entry : agent.disabledIntegrationTools()) {
            String trimmed = entry.trim();
            names.add(trimmed);
            CrmTool.findByKey(trimmed).ifPresent(tool -> names.add(tool.toolName()));
        }
        return names;
    }
}
