package uz.murodjon.uysotvoice.campaign.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.campaign.entity.Campaign;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link Campaign}. */
@Repository
public interface CampaignJpaRepository extends JpaRepository<Campaign, Long> {

    Optional<Campaign> findByIdAndCompanyId(long id, long companyId);

    List<Campaign> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);

    /** Every active campaign, across every company — deliberately unscoped, see {@link CampaignRepository#findActive}. */
    List<Campaign> findByStatusOrderById(String status);

    @Modifying @Transactional
    @Query("UPDATE Campaign c SET c.status = :status WHERE c.id = :id AND c.companyId = :companyId")
    void updateStatus(@Param("id") long id, @Param("status") String status, @Param("companyId") long companyId);
}
