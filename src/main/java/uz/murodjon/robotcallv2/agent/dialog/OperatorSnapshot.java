package uz.murodjon.robotcallv2.agent.dialog;

import java.util.Map;

/**
 * Context handed to a human operator when a call is transferred (PROJECT.md §11.6,
 * Stage 11). Served live from the still-open dialog session so the operator screen
 * sees who is calling, why, and the conversation so far.
 *
 * @param channelId      caller's channel
 * @param clientName     debtor name
 * @param debtAmount     outstanding amount (as text)
 * @param currency       currency label
 * @param dueDate        original due date (as text)
 * @param contractNumber contract reference
 * @param dialogState    FSM state when the transfer happened
 * @param transferReason why the model handed the call over, in its own words; null when it
 *                       gave none or the transfer came from elsewhere (a supervisor, a flow)
 * @param transcript     conversation so far ({@code ROLE: text} lines)
 * @param transferTo     the number the flow's CALL_TRANSFER stage the call is in hands it to; null
 *                       when the transfer goes where the agent sends its transfers
 * @param transferHeaders the custom SIP headers that stage puts on the transferred leg's INVITE, facts
 *                       filled in; empty when it sets none or the call is in no such stage
 */
public record OperatorSnapshot(
        String channelId,
        String clientName,
        String debtAmount,
        String currency,
        String dueDate,
        String contractNumber,
        String dialogState,
        String transferReason,
        String transcript,
        String transferTo,
        Map<String, String> transferHeaders
) {
    public OperatorSnapshot {
        transferHeaders = transferHeaders == null ? Map.of() : Map.copyOf(transferHeaders);
    }
}
