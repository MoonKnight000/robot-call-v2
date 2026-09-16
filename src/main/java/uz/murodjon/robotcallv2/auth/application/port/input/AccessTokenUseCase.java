package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;

/** What the security filter chain asks of an access token: whose is it, if it still counts. */
public interface AccessTokenUseCase {

    /**
     * The identity a token carries, or null when it is forged, expired, or was issued
     * before its user's last revocation (logout, password or role change, block).
     */
    AuthenticatedUser parse(String token);
}
