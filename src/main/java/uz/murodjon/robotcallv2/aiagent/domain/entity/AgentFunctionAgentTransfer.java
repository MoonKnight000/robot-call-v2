package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * Settings of an AGENT_TRANSFER function: the company's other agent the call continues with.
 *
 * @param agentId      the agent taking over; an agent of the same company, not this one
 * @param keepVoice    asks the new agent to speak in this call's voice; the handoff always keeps it
 * @param keepLanguage asks the new agent to continue in this call's language; the handoff always keeps it
 */
public record AgentFunctionAgentTransfer(Long agentId, boolean keepVoice, boolean keepLanguage) {
}
