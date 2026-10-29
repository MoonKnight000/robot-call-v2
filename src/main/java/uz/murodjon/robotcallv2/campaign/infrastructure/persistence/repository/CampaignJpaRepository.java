package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.campaign.domain.enums.RecurrenceType;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignEntity;
import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;
import uz.murodjon.robotcallv2.role.domain.enums.Feature;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link CampaignEntity}.
 */
@Repository
public interface CampaignJpaRepository extends JpaRepository<CampaignEntity, Long>, JpaSpecificationExecutor<CampaignEntity> {

    @Query("SELECT c FROM CampaignEntity c WHERE c.id = :id AND c.company.id = :companyId")
    Optional<CampaignEntity> findByIdAndCompanyId(@Param("id") long id, @Param("companyId") long companyId);

    @Query("SELECT c FROM CampaignEntity c WHERE c.company.id = :companyId")
    List<CampaignEntity> findByCompanyId(@Param("companyId") long companyId, Pageable pageable);

    @Query("SELECT count(c) FROM CampaignEntity c WHERE c.company.id = :companyId")
    long countByCompanyId(@Param("companyId") long companyId);

    /** The campaigns the dialer may work: in the given status, of a company in {@code companyStatus} that has not had {@code feature} switched off. */
    @Query("SELECT c FROM CampaignEntity c WHERE c.status = :status AND c.company.status = :companyStatus "
            + "AND NOT EXISTS (SELECT 1 FROM CompanyFeatureEntity f WHERE f.company = c.company "
            + "AND f.feature = :feature AND f.enabled = false) ORDER BY c.id")
    List<CampaignEntity> findDispatchable(@Param("status") CampaignStatus status,
                                          @Param("companyStatus") CompanyStatus companyStatus,
                                          @Param("feature") Feature feature);

    List<CampaignEntity> findByRecurrenceTypeNotAndStatusNot(RecurrenceType recurrenceType, CampaignStatus status);

    List<CampaignEntity> findByStatusAndStartAtLessThanEqual(CampaignStatus status, Instant now);

    List<CampaignEntity> findByStatusAndEndAtLessThanEqual(CampaignStatus status, Instant now);

    @Query("SELECT c FROM CampaignEntity c WHERE c.company.id = :companyId AND lower(c.name) LIKE :pattern ESCAPE '!' "
            + "ORDER BY c.id DESC")
    List<CampaignEntity> searchByCompanyId(@Param("companyId") long companyId, @Param("pattern") String pattern,
                                          Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignEntity c SET c.status = :status WHERE c.id = :id AND c.company.id = :companyId")
    void updateStatus(@Param("id") long id, @Param("status") CampaignStatus status, @Param("companyId") long companyId);

    /** Atomic ACTIVE → COMPLETED; 1 row when this call won, 0 when another already had or targets remain. */
    @Modifying
    @Transactional
    @Query(value = "UPDATE campaign SET status = 'COMPLETED' WHERE id = :id AND company_id = :companyId "
            + "AND status = 'ACTIVE' AND NOT EXISTS (SELECT 1 FROM campaign_target t WHERE t.campaign_id = :id "
            + "AND t.status IN ('PENDING', 'IN_PROGRESS'))", nativeQuery = true)
    int completeIfFinished(@Param("id") long id, @Param("companyId") long companyId);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignEntity c SET c.lastRunAt = :lastRunAt, c.status = :status WHERE c.id = :id")
    void recordRecurrenceRun(@Param("id") long id, @Param("lastRunAt") Instant lastRunAt, @Param("status") CampaignStatus status);
}
