package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link LearnedFloorRuleEntity}.
 */
@Repository
public interface LearnedFloorRuleJpaRepository extends JpaRepository<LearnedFloorRuleEntity, Long> {

    Optional<LearnedFloorRuleEntity> findByPhrase(String phrase);

    List<LearnedFloorRuleEntity> findAllByStatus(String status);
}
