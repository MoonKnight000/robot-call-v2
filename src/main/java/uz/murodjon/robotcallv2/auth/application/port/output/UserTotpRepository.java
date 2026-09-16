package uz.murodjon.robotcallv2.auth.application.port.output;

import uz.murodjon.robotcallv2.auth.domain.entity.UserTotp;

public interface UserTotpRepository {

    /** The user's secret, or null when they never started setting it up. */
    UserTotp find(long userId);

    /** Inserts or replaces the user's row. */
    void save(UserTotp totp);

    /**
     * Records that a code of {@code step} was used — only if no later-or-equal step was
     * used before. False means the code is a replay and must be refused.
     */
    boolean markUsed(long userId, long step);

    void delete(long userId);
}
