package uz.murodjon.robotcallv2.donotcall.application.dto;

/** Response for {@code POST /api/targets/{id}/do-not-call}. */
public record DoNotCallResponse(long targetId, boolean doNotCall) {
}
