package uz.murodjon.robotcallv2.conversion.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.conversion.infrastructure.persistence.entity.ConversionAttributionEntity;

import java.util.List;
import java.util.Optional;

public interface ConversionAttributionJpaRepository extends JpaRepository<ConversionAttributionEntity, Long> {

    Optional<ConversionAttributionEntity> findByConversionEventId(Long conversionEventId);

    @Query(value = """
            SELECT variant_id                             AS variant_id,
                   COUNT(*)                               AS conversions,
                   COALESCE(SUM(attributed_value_uzs), 0) AS value_uzs
              FROM conversion_attribution
             WHERE company_id = :companyId
               AND campaign_id = :campaignId
             GROUP BY variant_id
            """, nativeQuery = true)
    List<Object[]> findConversionsByVariant(@Param("companyId") Long companyId,
                                            @Param("campaignId") Long campaignId);
}
