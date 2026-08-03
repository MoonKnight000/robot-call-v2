package uz.murodjon.uysotvoice.scenario.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.scenario.entity.ScenarioEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link ScenarioEntity}. */
@Repository
public interface ScenarioJpaRepository extends JpaRepository<ScenarioEntity, Long> {

    /** Visible if it's a global builtin (null company_id) or belongs to the current company. */
    @Query("SELECT s FROM ScenarioEntity s WHERE s.id = :id AND (s.companyId = :companyId OR s.companyId IS NULL)")
    Optional<ScenarioEntity> findVisible(@Param("id") long id, @Param("companyId") long companyId);

    /**
     * Cheap id→name lookup for enriching other rows (e.g. {@code CampaignRow},
     * {@code InboundRoute}) without paying the JSON-definition parse cost of {@link #findVisible}.
     */
    @Query("SELECT s.id, s.name FROM ScenarioEntity s WHERE s.id IN :ids AND (s.companyId = :companyId OR s.companyId IS NULL)")
    List<Object[]> findNamesByIds(@Param("ids") Collection<Long> ids, @Param("companyId") long companyId);

    @Query("SELECT MAX(s.version) FROM ScenarioEntity s WHERE s.scenarioKey = :scenarioKey")
    Integer maxVersion(@Param("scenarioKey") String scenarioKey);

    boolean existsByScenarioKey(String scenarioKey);

    /** The active version of {@code scenarioKey} — at most one exists (idx_scenario_active_key). */
    Optional<ScenarioEntity> findByScenarioKeyAndActiveTrue(String scenarioKey);

    /**
     * Every scenario's current active version — never a superseded one (§10.7 list).
     * Includes global builtins plus the current company's own custom scenarios.
     * {@code builtinOnly} is nullable: the {@code IS NULL} branch of the JPQL leaves the
     * filter out entirely when the caller wants both.
     */
    @Query("SELECT s FROM ScenarioEntity s WHERE s.active = true AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "AND (:builtinOnly IS NULL OR s.builtin = :builtinOnly)")
    List<ScenarioEntity> findVisible(@Param("companyId") long companyId,
                                     @Param("builtinOnly") Boolean builtinOnly, Pageable pageable);

    @Query("SELECT COUNT(s) FROM ScenarioEntity s WHERE s.active = true AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "AND (:builtinOnly IS NULL OR s.builtin = :builtinOnly)")
    long countVisible(@Param("companyId") long companyId, @Param("builtinOnly") Boolean builtinOnly);

    /** Flips the current active version of {@code scenarioKey} off, ahead of inserting the next one. */
    @Modifying @Transactional
    @Query("UPDATE ScenarioEntity s SET s.active = false WHERE s.scenarioKey = :scenarioKey AND s.active = true")
    void deactivate(@Param("scenarioKey") String scenarioKey);
}
