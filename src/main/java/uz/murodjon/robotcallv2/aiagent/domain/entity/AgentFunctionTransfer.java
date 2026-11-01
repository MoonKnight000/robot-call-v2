package uz.murodjon.robotcallv2.aiagent.domain.entity;

import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTransferMode;

/**
 * Settings of a TRANSFER_CALL function.
 *
 * @param destination a phone number, a SIP endpoint ({@code PJSIP/...}, {@code sip:user@host}) or a
 *                    {@code {{variable}}} that resolves to one when the call is handed over; null or
 *                    blank means the transfer goes where the agent sent it before (stage number, queue,
 *                    transfer number, global operator)
 * @param mode        how the caller is handed over; null reads as {@link AgentTransferMode#COLD}
 */
public record AgentFunctionTransfer(String destination, AgentTransferMode mode) {

    public AgentFunctionTransfer {
        destination = destination == null || destination.isBlank() ? null : destination.trim();
        mode = mode == null ? AgentTransferMode.COLD : mode;
    }
}
