package uz.murodjon.robotcallv2.operator.application.dto;

/**
 * What an operator's browser needs to take a call transferred to them on the LiveKit engine.
 *
 * @param url      the LiveKit address to connect to ({@code wss://…})
 * @param token    an access token for that call's room only, short-lived
 * @param identity the participant the operator becomes in the room
 */
public record OperatorJoinResponse(String url, String token, String identity) {
}
