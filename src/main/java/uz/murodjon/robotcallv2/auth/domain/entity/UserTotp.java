package uz.murodjon.robotcallv2.auth.domain.entity;

import java.time.Instant;

/**
 * A user's authenticator-app secret (NEXT-PLAN S.3).
 *
 * @param secretEncrypted the Base32 secret, encrypted with the secret-vault key
 * @param enabled         false between setup and the first code the user proves
 * @param lastUsedStep    the 30-second step of the last code accepted; a code counts once
 */
public record UserTotp(
        long userId,
        long companyId,
        String secretEncrypted,
        boolean enabled,
        long lastUsedStep,
        Instant enabledAt
) {

    /** A secret just handed to the user, not yet proven. */
    public static UserTotp pending(long userId, long companyId, String secretEncrypted) {
        return new UserTotp(userId, companyId, secretEncrypted, false, 0, null);
    }

    /** The same secret, switched on by a code of step {@code usedStep}. */
    public UserTotp activate(long usedStep, Instant at) {
        return new UserTotp(userId, companyId, secretEncrypted, true, usedStep, at);
    }
}
