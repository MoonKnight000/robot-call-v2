package uz.murodjon.robotcallv2.scenario.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.scenario.infrastructure.persistence.entity.ScenarioEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScenarioJpaRepository extends JpaRepository<ScenarioEntity, Long> {

    @Query("SELECT s FROM ScenarioEntity s WHERE s.id = :id AND (s.companyId = :companyId OR s.companyId IS NULL)")
    Optional<ScenarioEntity> findVisible(@Param("id") long id, @Param("companyId") long companyId);

    @Query("SELECT s.id, s.name FROM ScenarioEntity s WHERE s.id IN :ids AND (s.companyId = :companyId OR s.companyId IS NULL)")
    List<Object[]> findNamesByIds(@Param("ids") Collection<Long> ids, @Param("companyId") long companyId);

    @Query("SELECT MAX(s.version) FROM ScenarioEntity s WHERE s.scenarioKey = :scenarioKey")
    Integer maxVersion(@Param("scenarioKey") String scenarioKey);

    boolean existsByScenarioKey(String scenarioKey);

    Optional<ScenarioEntity> findByScenarioKeyAndActiveTrue(String scenarioKey);

    List<ScenarioEntity> findByActiveTrue();

    @Query("SELECT s FROM ScenarioEntity s WHERE s.active = true AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "AND (:builtinOnly IS NULL OR s.builtin = :builtinOnly)")
    List<ScenarioEntity> findVisible(@Param("companyId") long companyId,
                                     @Param("builtinOnly") Boolean builtinOnly, Pageable pageable);

    @Query("SELECT COUNT(s) FROM ScenarioEntity s WHERE s.active = true AND (s.companyId = :companyId OR s.companyId IS NULL) "
            + "AND (:builtinOnly IS NULL OR s.builtin = :builtinOnly)")
    long countVisible(@Param("companyId") long companyId, @Param("builtinOnly") Boolean builtinOnly);

    @Modifying
    @Transactional
    @Query("UPDATE ScenarioEntity s SET s.active = false WHERE s.scenarioKey = :scenarioKey AND s.active = true")
    void deactivate(@Param("scenarioKey") String scenarioKey);
}
