package uz.murodjon.uysotvoice.siptrunk.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.siptrunk.entity.SipTrunkEntity;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link SipTrunkEntity}. */
@Repository
public interface SipTrunkJpaRepository extends JpaRepository<SipTrunkEntity, Long> {

    Optional<SipTrunkEntity> findByIdAndCompanyId(long id, long companyId);

    List<SipTrunkEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    boolean existsByCompanyId(long companyId);

    boolean existsByCompanyIdAndIsDefaultTrue(long companyId);

    /**
     * The runtime lookup {@code AriService} makes when originating a call (ROADMAP
     * B.3) — deliberately unscoped by {@code CurrentCompany}: the caller (the dialer,
     * on a campaign's behalf) already knows which company's trunk it needs, which is
     * not necessarily the company the current request/thread happens to be scoped to.
     */
    Optional<SipTrunkEntity> findByCompanyIdAndIsDefaultTrueAndEnabledTrue(long companyId);

    /** Flips the current default off, ahead of promoting a different trunk (or none). */
    @Modifying
    @Transactional
    @Query("UPDATE SipTrunkEntity t SET t.isDefault = false WHERE t.companyId = :companyId AND t.isDefault = true")
    void clearDefault(@Param("companyId") long companyId);
}
