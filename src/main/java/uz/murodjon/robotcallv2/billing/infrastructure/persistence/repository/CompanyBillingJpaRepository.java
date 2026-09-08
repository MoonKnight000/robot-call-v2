package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.CompanyBillingEntity;

import java.util.List;
import java.util.Optional;

public interface CompanyBillingJpaRepository extends JpaRepository<CompanyBillingEntity, Long> {

    Optional<CompanyBillingEntity> findByCompanyId(Long companyId);

    List<CompanyBillingEntity> findByAutoRechargeTrue();

    /**
     * Reads the balance and holds the row until the transaction ends.
     *
     * <p>Settling a call writes a ledger entry saying what the balance was before and
     * after. Without the lock two calls of the same company ending together would both
     * read the same "before" and the ledger would stop reconciling with the balance it
     * describes.
     */
    @Query(value = "SELECT balance_uzs FROM company_billing WHERE company_id = :companyId FOR UPDATE", nativeQuery = true)
    Optional<Long> findBalanceForUpdate(@Param("companyId") Long companyId);

    @Modifying
    @Query("UPDATE CompanyBillingEntity b SET b.balanceUzs = b.balanceUzs + :amount, b.updatedAt = CURRENT_TIMESTAMP WHERE b.company.id = :companyId")
    void addBalance(@Param("companyId") Long companyId, @Param("amount") Long amount);

    /**
     * Takes a hold, but only if the company can still cover it.
     *
     * <p>The check is in the WHERE clause rather than read-then-write in Java, because
     * the dialer runs this from several threads at once: two calls dialled at the same
     * moment against a balance big enough for one of them must not both pass.
     *
     * @return 1 when the hold was taken, 0 when there was not enough left
     */
    @Modifying
    @Query(value = """
            UPDATE company_billing
               SET reserved_uzs = reserved_uzs + :amount, updated_at = now()
             WHERE company_id = :companyId
               AND balance_uzs - reserved_uzs >= :amount
            """, nativeQuery = true)
    int reserve(@Param("companyId") Long companyId, @Param("amount") Long amount);

    /** Gives a hold back without charging for it. */
    @Modifying
    @Query(value = """
            UPDATE company_billing
               SET reserved_uzs = GREATEST(0, reserved_uzs - :amount), updated_at = now()
             WHERE company_id = :companyId
            """, nativeQuery = true)
    void release(@Param("companyId") Long companyId, @Param("amount") Long amount);

    /**
     * Charges a finished call: the hold goes back and the balance drops by what the call
     * actually cost, which is rarely the same number.
     */
    @Modifying
    @Query(value = """
            UPDATE company_billing
               SET reserved_uzs = GREATEST(0, reserved_uzs - :held),
                   balance_uzs = balance_uzs - :charge,
                   updated_at = now()
             WHERE company_id = :companyId
            """, nativeQuery = true)
    void charge(@Param("companyId") Long companyId, @Param("held") Long held, @Param("charge") Long charge);
}
