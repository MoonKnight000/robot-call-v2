package uz.murodjon.robotcallv2.auth.application.dto;

import java.time.Instant;

/**
 * @param email               where the activation link went
 * @param activationExpiresAt after this the link no longer works
 */
public record SignupResponse(
        long companyId,
        String email,
        Instant activationExpiresAt
) {
}
