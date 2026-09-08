package uz.murodjon.robotcallv2.billing.application.port.output;

import uz.murodjon.robotcallv2.billing.domain.entity.CompanyBilling;

import java.util.List;
import java.util.Optional;

public interface CompanyBillingRepository {

    Optional<CompanyBilling> findByCompanyId(long companyId);

    /** Every company that asked to be topped up automatically. */
    List<CompanyBilling> findByAutoRechargeEnabled();

    /**
     * Reads the balance and holds the row until the caller's transaction ends, so that
     * two calls of the same company settling together cannot both write a ledger entry
     * claiming the same starting balance.
     *
     * @return the balance, or empty when the company has no billing row yet
     */
    Optional<Long> findBalanceForUpdate(long companyId);

    CompanyBilling save(CompanyBilling billing);

    void addBalance(long companyId, long amountUzs);

    /**
     * Holds {@code amountUzs} against the company's balance if it can still cover it.
     *
     * <p>The decision is storage's, not the service's, so that two calls dialled at the
     * same instant against a balance big enough for one cannot both be allowed through.
     *
     * @return true when the hold was taken
     */
    boolean reserve(long companyId, long amountUzs);

    /** Gives a hold back without charging for it. */
    void release(long companyId, long amountUzs);

    /** Releases {@code heldUzs} and takes {@code chargeUzs} off the balance. */
    void charge(long companyId, long heldUzs, long chargeUzs);
}
