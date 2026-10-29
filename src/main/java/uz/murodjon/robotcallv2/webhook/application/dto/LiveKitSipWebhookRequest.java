package uz.murodjon.robotcallv2.webhook.application.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * Incoming webhook payload from LiveKit SIP Gateway on an inbound call dispatch.
 *
 * @param callId LiveKit SIP Call ID
 * @param caller caller phone number (CLI / from)
 * @param called dialed DID number (to)
 * @param roomName existing or pre-assigned room name (if any)
 * @param sipTrunkId SIP Trunk identifier
 */
public record LiveKitSipWebhookRequest(
        @JsonAlias({"sip_call_id", "call_id", "sipCallId"})
        String callId,

        @JsonAlias({"caller_number", "callerNumber", "from", "phone"})
        String caller,

        @JsonAlias({"called_number", "calledNumber", "to", "did", "trunk_phone_number", "trunkPhoneNumber"})
        String called,

        @JsonAlias({"room_name", "roomName"})
        String roomName,

        @JsonAlias({"sip_trunk_id", "trunk_id", "trunkId", "sipTrunkId"})
        String sipTrunkId
) {
    public String resolveDid() {
        return called != null && !called.isBlank() ? called : "";
    }

    public String resolveCaller() {
        return caller != null && !caller.isBlank() ? caller : "";
    }
}
