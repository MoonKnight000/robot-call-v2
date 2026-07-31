package uz.murodjon.uysotvoice.scenario.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.scenario.entity.Scenario;

import java.util.List;
import java.util.Optional;

/** Spring Data repository for {@link Scenario}. */
@Repository
public interface ScenarioJpaRepository extends JpaRepository<Scenario, Long> {

    /** Visible if it's a global builtin (null company_id) or belongs to the current company. */
    @Query("SELECT s FROM Scenario s WHERE s.id = :id AND (s.companyId = :companyId OR s.companyId IS NULL)")
    Optional<Scenario> findVisible(@Param("id") long id, @Param("companyId") long companyId);

    @Query("SELECT MAX(s.version) FROM Scenario s WHERE s.scenarioKey = :scenarioKey")
    Integer maxVersion(@Param("scenarioKey") String scenarioKey);

    boolean existsByScenarioKey(String scenarioKey);

    /**
     * Every scenario's current active version — never a superseded one (§10.7 list).
     * Includes global builtins plus the current company's own custom scenarios.
     * {@code builtinOnly} is nullable: the {@code IS NULL} branch of the JPQL leaves the
     * filter out entirely when the caller wants both.
     */
    @Query("SELECT s FROM Scenario s WHERE s.active = true AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "AND (:builtinOnly IS NULL OR s.builtin = :builtinOnly)")
    List<Scenario> findVisible(@Param("companyId") long companyId,
                                     @Param("builtinOnly") Boolean builtinOnly, Pageable pageable);

    @Query("SELECT COUNT(s) FROM Scenario s WHERE s.active = true AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "AND (:builtinOnly IS NULL OR s.builtin = :builtinOnly)")
    long countVisible(@Param("companyId") long companyId, @Param("builtinOnly") Boolean builtinOnly);

    /** Flips the current active version of {@code scenarioKey} off, ahead of inserting the next one. */
    @Modifying @Transactional
    @Query("UPDATE Scenario s SET s.active = false WHERE s.scenarioKey = :scenarioKey AND s.active = true")
    void deactivate(@Param("scenarioKey") String scenarioKey);
}
