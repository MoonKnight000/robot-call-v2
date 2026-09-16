package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.TwoFactorSetupResponse;

/** Two-factor login with an authenticator app (NEXT-PLAN S.3). */
public interface TwoFactorUseCase {

    /** A new secret for the user; not in force until {@link #enable} proves a code from it. */
    TwoFactorSetupResponse startTwoFactorSetup(long companyId, long userId, String accountName);

    /** Switches two-factor login on with the first code from the new secret. */
    void enable(long companyId, long userId, String code);

    /** Switches it off; the caller has already checked the user's password. */
    void disable(long companyId, long userId, String code);

    boolean isEnabled(long userId);

    /** Whether {@code code} is valid now and unused; accepting it uses it up. */
    boolean verifyCode(long userId, String code);
}
