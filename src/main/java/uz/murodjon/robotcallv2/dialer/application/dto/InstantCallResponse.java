package uz.murodjon.robotcallv2.dialer.application.dto;

/** Answer to {@code POST /api/calls/instant}: the queued target and the state it was queued in. */
public record InstantCallResponse(long targetId, String status) {

    public static InstantCallResponse queued(long targetId) {
        return new InstantCallResponse(targetId, "QUEUED_INSTANT");
    }
}
