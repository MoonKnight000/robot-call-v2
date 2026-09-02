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

    List<CampaignEntity> findByStatusOrderById(CampaignStatus status);

    List<CampaignEntity> findByRecurrenceTypeNotAndStatusNot(RecurrenceType recurrenceType, CampaignStatus status);

    @Query("SELECT c FROM CampaignEntity c WHERE c.company.id = :companyId AND lower(c.name) LIKE :pattern "
            + "ORDER BY c.id DESC")
    List<CampaignEntity> searchByCompanyId(@Param("companyId") long companyId, @Param("pattern") String pattern,
                                          Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignEntity c SET c.status = :status WHERE c.id = :id AND c.company.id = :companyId")
    void updateStatus(@Param("id") long id, @Param("status") CampaignStatus status, @Param("companyId") long companyId);

    @Modifying
    @Transactional
    @Query("UPDATE CampaignEntity c SET c.lastRunAt = :lastRunAt, c.status = :status WHERE c.id = :id")
    void recordRecurrenceRun(@Param("id") long id, @Param("lastRunAt") Instant lastRunAt, @Param("status") CampaignStatus status);
}
