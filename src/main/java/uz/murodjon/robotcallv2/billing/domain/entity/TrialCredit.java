package uz.murodjon.robotcallv2.billing.domain.entity;

import java.time.Instant;

/**
 * Free money a self-registered company was given to try the platform with.
 *
 * <p>The money itself sits in the ordinary balance — calls do not know it is a trial. This
 * record only remembers how much was given and until when, so that the unspent part can be
 * taken back once {@code expiresAt} passes.
 *
 * @param id          storage's, null before it is written
 * @param companyId   who got it
 * @param creditUzs   how much was given
 * @param grantedAt   when
 * @param expiresAt   when the unspent part is taken back
 * @param settledAt   when it was taken back; null while the trial runs
 * @param clawbackUzs how much was taken back; null while the trial runs
 */
public record TrialCredit(
        Long id,
        long companyId,
        long creditUzs,
        Instant grantedAt,
        Instant expiresAt,
        Instant settledAt,
        Long clawbackUzs
) {
    /** A trial about to be granted; the id is storage's. */
    public static TrialCredit granted(long companyId, long creditUzs, Instant grantedAt, Instant expiresAt) {
        return new TrialCredit(null, companyId, creditUzs, grantedAt, expiresAt, null, null);
    }

    public boolean isSettled() {
        return settledAt != null;
    }
}
