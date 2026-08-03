package uz.murodjon.uysotvoice.callrecord.dto;

/** Response for {@code POST /api/calls/{channelId}/hangup} — "Tugatish" (§10.3). */
public record HangupResponse(String channelId, String status) {
}
