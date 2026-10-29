package uz.murodjon.robotcallv2.phonenumber.domain.entity;

/** What reaching a SIP host told us; {@code status} is CONNECTED, REACHABLE or FAILED. */
public record SipProbeResult(boolean connected, long roundTripMs, String status, String details) {
}
