package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentFunction;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentFunctionType;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.scenario.domain.enums.StageType;

import java.util.Optional;

/**
 * Where a human handover can go, decided in one place for the tool lists, the prompts and the
 * transfer itself. A target resolves in this order: the number of the CALL_TRANSFER stage the call
 * is in, the destination of the agent's TRANSFER_CALL function, the agent's transfer queue, the
 * agent's transfer number, the global operator. When none exists the transfer just hangs up, so
 * {@code requestHumanTransfer} is not offered at all — nor is it for an agent whose saved function list
 * has no TRANSFER_CALL function, whatever targets exist.
 */
public final class TransferTargets {

    private TransferTargets() {
    }

    /**
     * The endpoint of the platform-wide operator, or null when none is configured. The last
     * fallback of a transfer, after the stage number, the agent's queue and its number.
     */
    public static String findGlobalOperatorEndpoint(OperatorProperties operatorProperties) {
        return operatorProperties.enabled() && operatorProperties.endpoint() != null
                && !operatorProperties.endpoint().isBlank() ? operatorProperties.endpoint() : null;
    }

    /**
     * Whether a target exists for the whole call, whichever stage it is in: the destination of the agent's
     * TRANSFER_CALL function, its own queue or number, or the global operator. Fixed when the call starts.
     */
    public static boolean hasCallLevelTarget(AiAgent agent, OperatorProperties operatorProperties) {
        Optional<AgentFunction> function = AgentFunctionPlan.find(agent, AgentFunctionType.TRANSFER_CALL);
        if (AgentFunctionPlan.isConfigured(agent) && function.isEmpty()) {
            return false;
        }
        if (function.isPresent() && function.get().transfer() != null && function.get().transfer().destination() != null) {
            return true;
        }
        return agent != null && agent.callBehaviour().transferEnabled()
                || findGlobalOperatorEndpoint(operatorProperties) != null;
    }

    /**
     * Whether {@code requestHumanTransfer} can be offered while the call is in {@code state}: the agent's
     * function list allows it, and a call-level target exists or this stage names a number of its own.
     */
    static boolean isOffered(AiAgent agent, boolean callLevelTarget, ScenarioDefinition scenario, String state) {
        return AgentFunctionPlan.offersTransfer(agent)
                && (callLevelTarget || isNumber(StageEntry.findTransferTarget(scenario, state)));
    }

    /**
     * Whether the tool can be offered at some stage of the call — what a prompt written once
     * for the whole call may assume, and what a realtime engine declares once at connect.
     */
    static boolean isOfferedAnywhere(AiAgent agent, boolean callLevelTarget, ScenarioDefinition scenario) {
        if (!AgentFunctionPlan.offersTransfer(agent)) {
            return false;
        }
        if (callLevelTarget) {
            return true;
        }
        if (scenario == null || scenario.stages() == null) {
            return false;
        }
        return scenario.stages().stream().map(TransferTargets::findStageNumber).anyMatch(TransferTargets::isNumber);
    }

    private static String findStageNumber(StageDef stage) {
        return stage.type() == StageType.CALL_TRANSFER && stage.action() != null ? stage.action().transferTo() : null;
    }

    private static boolean isNumber(String number) {
        return number != null && !number.isBlank();
    }
}
