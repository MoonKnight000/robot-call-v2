package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.SipTrunkEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for SipTrunkEntity. */
@Repository
public interface SipTrunkJpaRepository extends JpaRepository<SipTrunkEntity, Long> {

    Optional<SipTrunkEntity> findByIdAndCompanyId(long id, long companyId);

    List<SipTrunkEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    boolean existsByCompanyId(long companyId);

    boolean existsByCompanyIdAndIsDefaultTrue(long companyId);

    Optional<SipTrunkEntity> findByCompanyIdAndIsDefaultTrueAndEnabledTrue(long companyId);

    List<SipTrunkEntity> findByCompanyIdAndEnabledTrue(long companyId);

    List<SipTrunkEntity> findByIdInAndCompanyIdAndEnabledTrue(Collection<Long> ids, long companyId);

    @Modifying
    @Transactional
    @Query("UPDATE SipTrunkEntity t SET t.isDefault = false WHERE t.company.id = :companyId AND t.isDefault = true")
    void clearDefault(@Param("companyId") long companyId);

    List<SipTrunkEntity> findByHostIsNotNullAndEnabledTrue();
}
