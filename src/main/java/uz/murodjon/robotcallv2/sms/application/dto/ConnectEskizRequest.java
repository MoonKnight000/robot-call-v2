package uz.murodjon.robotcallv2.sms.application.dto;

/**
 * PUT /api/settings/integrations/eskiz body.
 *
 * @param sender the sender name shown to the recipient; blank — the platform default
 */
public record ConnectEskizRequest(String email, String password, String sender) {
}
