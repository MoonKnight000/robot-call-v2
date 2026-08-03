package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.campaign.entity.CampaignEntity;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link CampaignEntity}. */
@Repository
public interface CampaignJpaRepository extends JpaRepository<CampaignEntity, Long> {

    Optional<CampaignEntity> findByIdAndCompanyId(long id, long companyId);

    List<CampaignEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    @Query("SELECT c FROM CampaignEntity c WHERE c.companyId = :companyId AND (:status IS NULL OR c.status = :status)")
    List<CampaignEntity> findByCompanyId(@Param("companyId") long companyId, @Param("status") CampaignStatus status, Pageable pageable);

    @Query("SELECT count(c) FROM CampaignEntity c WHERE c.companyId = :companyId AND (:status IS NULL OR c.status = :status)")
    long countByCompanyId(@Param("companyId") long companyId, @Param("status") CampaignStatus status);

    /** Every active campaign, across every company — deliberately unscoped, see {@link CampaignRepository#findActive}. */
    List<CampaignEntity> findByStatusOrderById(CampaignStatus status);

    /** Command palette (UI-DESIGN §11.9) — top matches by name, most recent first. */
    @Query("SELECT c FROM CampaignEntity c WHERE c.companyId = :companyId AND lower(c.name) LIKE :pattern "
            + "ORDER BY c.id DESC")
    List<CampaignEntity> searchByCompanyId(@Param("companyId") long companyId, @Param("pattern") String pattern,
                                            Pageable pageable);

    @Modifying @Transactional
    @Query("UPDATE CampaignEntity c SET c.status = :status WHERE c.id = :id AND c.companyId = :companyId")
    void updateStatus(@Param("id") long id, @Param("status") CampaignStatus status, @Param("companyId") long companyId);
}
