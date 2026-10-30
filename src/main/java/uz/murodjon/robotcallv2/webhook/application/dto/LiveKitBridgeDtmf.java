package uz.murodjon.robotcallv2.webhook.application.dto;

/**
 * One key a caller pressed, as the media bridge reports it.
 *
 * @param room  the call's room (its channel id)
 * @param digit the key: {@code 0-9}, {@code *} or {@code #}
 */
public record LiveKitBridgeDtmf(String room, String digit) {
}
