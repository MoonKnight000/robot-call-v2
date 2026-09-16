package uz.murodjon.robotcallv2.agent.flow;

import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;

/**
 * The verdict on an inbound call ({@link InboundCallGate}).
 *
 * @param rule       the caller rule that matched, if any
 * @param graph      the published flow, for FLOW
 * @param message    what to say before hanging up, for CLOSED and a spoken REJECT; null for silence
 * @param language   the company's default language — what a message is said in
 * @param vip        a VIP caller rule matched
 * @param afterHours CLOSED because of the hours — the caller joins the missed-call list
 */
public record InboundDecision(
        InboundDecisionKind kind,
        InboundRoute route,
        InboundCallerRule rule,
        CallFlowGraph graph,
        String message,
        String language,
        boolean vip,
        boolean afterHours
) {

    public static InboundDecision noRoute() {
        return new InboundDecision(InboundDecisionKind.NO_ROUTE, null, null, null, null, null, false, false);
    }

    public static InboundDecision reject(InboundRoute route, InboundCallerRule rule, String language) {
        return new InboundDecision(InboundDecisionKind.REJECT, route, rule, null, rule.message(), language, false, false);
    }

    public static InboundDecision closed(InboundRoute route, String message, String language, boolean vip,
                                         boolean afterHours) {
        return new InboundDecision(InboundDecisionKind.CLOSED, route, null, null, message, language, vip, afterHours);
    }

    public static InboundDecision flow(InboundRoute route, CallFlowGraph graph, String language, boolean vip) {
        return new InboundDecision(InboundDecisionKind.FLOW, route, null, graph, null, language, vip, false);
    }

    public static InboundDecision agent(InboundRoute route, String language, boolean vip) {
        return new InboundDecision(InboundDecisionKind.AGENT, route, null, null, null, language, vip, false);
    }
}
