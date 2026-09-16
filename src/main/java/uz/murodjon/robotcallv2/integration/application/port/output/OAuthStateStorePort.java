package uz.murodjon.robotcallv2.integration.application.port.output;

import java.time.Duration;

/**
 * One-time OAuth {@code state} values and the company each was issued for. A state is
 * good for one callback: {@link #consume} forgets it as it answers.
 */
public interface OAuthStateStorePort {

    /** @return false when the store could not take it (the connect flow cannot start) */
    boolean save(String state, long companyId, Duration ttl);

    /** The company {@code state} was issued for, removed in the same step; null when unknown, used or expired. */
    Long consume(String state);
}
