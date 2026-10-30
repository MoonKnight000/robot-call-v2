package uz.murodjon.robotcallv2.agent.telephony;

/**
 * What a browser needs to join a call's room as a person — an operator taking a transferred
 * call, a supervisor watching one.
 *
 * @param url      the address to connect to ({@code wss://…})
 * @param token    an access token for that call's room only, short-lived
 * @param identity the participant the person becomes in the room
 */
public record WebRtcJoin(String url, String token, String identity) {
}
