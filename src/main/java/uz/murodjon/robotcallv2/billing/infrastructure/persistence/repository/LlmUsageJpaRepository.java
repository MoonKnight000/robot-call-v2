package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.LlmUsageEntity;

import java.time.Instant;
import java.util.List;

public interface LlmUsageJpaRepository extends JpaRepository<LlmUsageEntity, Long> {

    /** Lines written in {@code [from, to)}, summed per model and purpose. */
    @Query(value = """
            SELECT model,
                   purpose,
                   BOOL_OR(listed)                AS listed,
                   COALESCE(SUM(input_tokens), 0)  AS input_tokens,
                   COALESCE(SUM(output_tokens), 0) AS output_tokens,
                   COALESCE(SUM(cached_tokens), 0) AS cached_tokens,
                   COALESCE(SUM(input_uzs), 0)     AS input_uzs,
                   COALESCE(SUM(output_uzs), 0)    AS output_uzs,
                   COALESCE(SUM(cached_uzs), 0)    AS cached_uzs
              FROM llm_usage
             WHERE company_id = :companyId
               AND created_at >= :from
               AND created_at < :to
             GROUP BY model, purpose
            """, nativeQuery = true)
    List<Object[]> findModelSpend(@Param("companyId") long companyId, @Param("from") Instant from,
                                  @Param("to") Instant to);
}
