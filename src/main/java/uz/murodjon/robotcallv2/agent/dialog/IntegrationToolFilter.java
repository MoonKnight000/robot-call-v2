package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;

import java.util.HashSet;
import java.util.Set;

/**
 * Which of the company's enabled integration tools an agent is offered ({@link IntegrationHttpToolFactory}).
 * One rule for both pipelines.
 *
 * <p>An agent that saved a function list is offered exactly its INTEGRATION functions ({@link
 * AgentFunctionPlan#findAllowedIntegrationNames}); any other agent is offered everything the company enabled
 * except what it switched off ({@code AiAgent#disabledIntegrationTools}).
 *
 * <p>An entry is the name the model calls a tool by ({@code createLead}). A name the catalog no longer
 * has matches nothing and is ignored.
 */
final class IntegrationToolFilter {

    private IntegrationToolFilter() {
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
        }
        return names;
    }
}
