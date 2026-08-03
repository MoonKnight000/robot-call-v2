package uz.murodjon.uysotvoice.callrecord.dto;

/** Response for {@code POST /api/calls/{channelId}/transfer} — "Operatorga uzatish" (§10.3). */
public record TransferResponse(String channelId, String status) {
}
