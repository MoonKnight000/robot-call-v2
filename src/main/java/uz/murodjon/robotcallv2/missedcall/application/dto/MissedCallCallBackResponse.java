package uz.murodjon.robotcallv2.missedcall.application.dto;

/**
 * POST /api/missed-calls/{id}/call-back — the operator's phone is ringing; once they pick
 * up, the caller is dialled.
 *
 * @param operatorChannelId the Asterisk channel ringing the operator
 */
public record MissedCallCallBackResponse(
        long missedCallId,
        String phone,
        String operatorChannelId
) {
}
