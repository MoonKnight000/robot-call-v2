package uz.murodjon.robotcallv2.campaign.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.campaign.infrastructure.persistence.entity.CampaignVariantEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignVariantJpaRepository extends JpaRepository<CampaignVariantEntity, Long> {

    Optional<CampaignVariantEntity> findByIdAndCompanyId(Long id, Long companyId);

    List<CampaignVariantEntity> findAllByCampaignIdAndCompanyId(Long campaignId, Long companyId);

    @Modifying
    @Query("UPDATE CampaignVariantEntity v SET v.callsCount = v.callsCount + 1, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    void incrementCallsCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE CampaignVariantEntity v SET v.answeredCount = v.answeredCount + 1, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    void incrementAnsweredCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE CampaignVariantEntity v SET v.convertedCount = v.convertedCount + 1, v.updatedAt = CURRENT_TIMESTAMP WHERE v.id = :id")
    void incrementConvertedCount(@Param("id") Long id);

    void deleteByIdAndCompanyId(Long id, Long companyId);
}
