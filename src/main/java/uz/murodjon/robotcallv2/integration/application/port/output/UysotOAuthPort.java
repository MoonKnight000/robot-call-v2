package uz.murodjon.robotcallv2.integration.application.port.output;

import uz.murodjon.robotcallv2.integration.domain.entity.OAuthTokenReply;

import java.io.IOException;

/**
 * Uysot's OAuth endpoints, called with the configured client credentials. Transport
 * failures are thrown as they come — CrmIntegrationService decides what each one means.
 */
public interface UysotOAuthPort {

    OAuthTokenReply exchangeCode(String code) throws IOException, InterruptedException;

    OAuthTokenReply refreshToken(String refreshToken) throws IOException, InterruptedException;

    void revokeToken(String token) throws IOException, InterruptedException;
}
