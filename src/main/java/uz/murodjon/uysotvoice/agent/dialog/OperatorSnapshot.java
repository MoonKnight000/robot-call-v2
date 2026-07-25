package uz.murodjon.uysotvoice.agent.dialog;

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
 * @param transcript     conversation so far ({@code ROLE: text} lines)
 */
public record OperatorSnapshot(
        String channelId,
        String clientName,
        String debtAmount,
        String currency,
        String dueDate,
        String contractNumber,
        String dialogState,
        String transcript
) {
}
