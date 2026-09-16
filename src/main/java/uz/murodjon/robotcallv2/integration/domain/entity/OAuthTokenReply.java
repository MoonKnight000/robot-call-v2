package uz.murodjon.robotcallv2.integration.domain.entity;

/** What the OAuth token endpoint answered: its HTTP status and raw JSON body. */
public record OAuthTokenReply(int statusCode, String body) {
}
