package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunctionSms;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentSmsRecipient;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the agent's saved {@code functions} list decides about a call's tools, for both pipelines. An agent
 * that never saved a list ({@link #isConfigured} false) is not touched by any of this: every question here
 * then answers as the older per-feature fields do, so its calls behave as before.
 *
 * <p>Once a list is saved it is the whole truth for the agent-level tools (hang up, hand over, press keys,
 * text, extract, attached tools, integration tools). The scenario's own outcome tools and tools the engine
 * needs to work (knowledge search, facts, language) are not functions and stay as they were.
 */
final class AgentFunctionPlan {

    /**
     * What a resolved transfer destination may look like. It can be built from call variables a caller's own
     * words reached, so anything beyond a plain number or a bare endpoint name is refused.
     */
    private static final Pattern DIALABLE_DESTINATION = Pattern.compile(
            "(?i)(\\+?[0-9]{3,15}|(PJSIP|SIP)/[A-Za-z0-9_.+@-]{1,100}|sips?:[A-Za-z0-9_.+@:-]{3,150})");

    private static final Pattern SMS_NUMBER = Pattern.compile("\\+?[0-9]{7,15}");

    private AgentFunctionPlan() {
    }

    /** Whether the agent saved a function list — from then on the runtime reads only that. */
    static boolean isConfigured(AiAgent agent) {
        return agent != null && agent.functions() != null;
    }

    /** The agent's function of {@code type}, when it saved a list that has one. */
    static Optional<AgentFunction> find(AiAgent agent, AgentFunctionType type) {
        if (!isConfigured(agent)) {
            return Optional.empty();
        }
        return agent.functions().stream().filter(function -> function.type() == type).findFirst();
    }

    /** Whether the model may hang up by itself: always for an unconfigured agent, else only with END_CALL. */
    static boolean offersEndCall(AiAgent agent) {
        return !isConfigured(agent) || find(agent, AgentFunctionType.END_CALL).isPresent();
    }

    /**
     * Whether the model may be offered {@code requestHumanTransfer}. An unconfigured agent follows the
     * older target rules ({@link TransferTargets}); a configured one needs a TRANSFER_CALL function.
     */
    static boolean offersTransfer(AiAgent agent) {
        return !isConfigured(agent) || find(agent, AgentFunctionType.TRANSFER_CALL).isPresent();
    }

    /** Whether the model may press keypad digits on the line itself. */
    static boolean offersPressDigit(AiAgent agent) {
        if (agent == null) {
            return false;
        }
        return isConfigured(agent) ? find(agent, AgentFunctionType.PRESS_DIGIT).isPresent()
                : agent.callBehaviour().pressDigitEnabled();
    }

    /** Whether the model may text during the call. */
    static boolean offersSms(AiAgent agent) {
        if (agent == null) {
            return false;
        }
        return isConfigured(agent) ? find(agent, AgentFunctionType.SEND_SMS).isPresent()
                : agent.callBehaviour().midCallSmsEnabled();
    }

    /** Whether the model may hand the call to another agent of the company by itself. */
    static boolean offersAgentTransfer(AiAgent agent) {
        return find(agent, AgentFunctionType.AGENT_TRANSFER).isPresent();
    }

    /** The EXTRACT_VARIABLE functions of a configured agent. */
    static List<AgentFunction> findExtractFunctions(AiAgent agent) {
        if (!isConfigured(agent)) {
            return List.of();
        }
        return agent.functions().stream().filter(function -> function.type() == AgentFunctionType.EXTRACT_VARIABLE).toList();
    }

    /**
     * Whether a company tool of this agent is offered: every attached tool for an unconfigured agent, only
     * the CUSTOM functions' tools for a configured one.
     */
    static boolean offersCustomTool(AiAgent agent, Tool tool) {
        if (!isConfigured(agent)) {
            return true;
        }
        return agent.functions().stream()
                .anyMatch(function -> function.type() == AgentFunctionType.CUSTOM && function.toolId() != null
                        && function.toolId().equals(tool.id()));
    }

    /**
     * The integration tools a configured agent allows, by the name the model calls each by; null for an unconfigured agent, which
     * follows {@code disabledIntegrationTools} instead.
     */
    static Set<String> findAllowedIntegrationNames(AiAgent agent) {
        if (!isConfigured(agent)) {
            return null;
        }
        Set<String> names = new HashSet<>();
        for (AgentFunction function : agent.functions()) {
            if (function.type() == AgentFunctionType.INTEGRATION && function.integrationTool() != null) {
                String entry = function.integrationTool().trim();
                names.add(entry);
            }
        }
        return names;
    }

    /**
     * Each function of a configured agent under the name the model calls it by, so a description override or
     * a waiting line finds its tool. A CUSTOM function is matched to its attached tool by id, since the tool
     * may have been renamed since the list was saved; an unmatched one is left out.
     */
    static Map<String, AgentFunction> indexByModelName(AiAgent agent, List<Tool> attachedTools) {
        Map<String, AgentFunction> byName = new LinkedHashMap<>();
        if (!isConfigured(agent)) {
            return byName;
        }
        for (AgentFunction function : agent.functions()) {
            switch (function.type()) {
                case INTEGRATION -> {
                    String entry = function.integrationTool();
                    if (entry != null) {
                        byName.put(entry, function);
                    }
                }
                case CUSTOM -> {
                    if (attachedTools != null) {
                        for (Tool tool : attachedTools) {
                            if (tool.id() != null && tool.id().equals(function.toolId())) {
                                byName.put(HttpToolExecutor.sanitizeToolName(tool.name()), function);
                            }
                        }
                    }
                }
                case EXTRACT_VARIABLE -> byName.put(function.name(), function);
                default -> byName.put(function.type().systemToolName(), function);
            }
        }
        return byName;
    }

    /**
     * Where this call's handover goes by number: the CALL_TRANSFER stage's own, else the destination of the agent's
     * TRANSFER_CALL function; null when neither names one, and the transfer goes where the agent sent it before.
     */
    static String findTransferNumber(DialogOutcomeSink session) {
        String stageNumber = StageEntry.findTransferTarget(session.scenario(), session.state());
        return stageNumber != null && !stageNumber.isBlank() ? stageNumber : findTransferDestination(session);
    }

    /**
     * The destination the agent's TRANSFER_CALL function names, its {@code {{variables}}} filled from this call,
     * or null when it names none, resolves to nothing, or to something that is not a number or an endpoint —
     * the transfer then goes where the agent sent it before.
     */
    static String findTransferDestination(DialogOutcomeSink session) {
        Optional<AgentFunction> function = find(session.agent(), AgentFunctionType.TRANSFER_CALL);
        String destination = function.map(AgentFunction::transfer).map(transfer -> transfer.destination()).orElse(null);
        if (destination == null) {
            return null;
        }
        String rendered = PromptTemplateEngine.render(destination, StageEntry.variables(session));
        if (rendered == null || rendered.isBlank() || !DIALABLE_DESTINATION.matcher(rendered.trim()).matches()) {
            return null;
        }
        return rendered.trim();
    }

    /** The settings of the agent's SEND_SMS function; empty for an unconfigured agent or one without it. */
    static Optional<AgentFunctionSms> findSms(AiAgent agent) {
        return find(agent, AgentFunctionType.SEND_SMS).map(AgentFunction::sms);
    }

    /**
     * The number the agent's SEND_SMS function texts instead of the caller, its {@code {{variables}}} filled from
     * this call; null when it texts the caller, or the number does not resolve to one.
     */
    static String findSmsNumber(DialogOutcomeSink session) {
        AgentFunctionSms sms = findSms(session.agent()).orElse(null);
        if (sms == null || sms.sendTo() != AgentSmsRecipient.NUMBER || sms.number() == null) {
            return null;
        }
        String rendered = PromptTemplateEngine.render(sms.number(), StageEntry.variables(session));
        String digits = rendered == null ? "" : rendered.replaceAll("[\\s()-]", "");
        return SMS_NUMBER.matcher(digits).matches() ? digits : null;
    }
}
