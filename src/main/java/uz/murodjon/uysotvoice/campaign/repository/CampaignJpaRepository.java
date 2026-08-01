package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.campaign.entity.Campaign;
import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link Campaign}. */
@Repository
public interface CampaignJpaRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByIdAndCompanyId(long id, long companyId);

    List<Campaign> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    @Query("SELECT c FROM Campaign c WHERE c.companyId = :companyId AND (:status IS NULL OR c.status = :status)")
    List<Campaign> findByCompanyId(@Param("companyId") long companyId, @Param("status") CampaignStatus status, Pageable pageable);

    @Query("SELECT count(c) FROM Campaign c WHERE c.companyId = :companyId AND (:status IS NULL OR c.status = :status)")
    long countByCompanyId(@Param("companyId") long companyId, @Param("status") CampaignStatus status);

    /** Every active campaign, across every company — deliberately unscoped, see {@link CampaignRepository#findActive}. */
    List<Campaign> findByStatusOrderById(CampaignStatus status);

    @Modifying @Transactional
    @Query("UPDATE Campaign c SET c.status = :status WHERE c.id = :id AND c.companyId = :companyId")
    void updateStatus(@Param("id") long id, @Param("status") CampaignStatus status, @Param("companyId") long companyId);
}
