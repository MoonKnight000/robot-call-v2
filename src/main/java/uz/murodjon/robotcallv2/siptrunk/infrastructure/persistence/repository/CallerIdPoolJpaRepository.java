package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.CallerIdPoolEntity;

import java.util.List;
import java.util.Optional;

public interface CallerIdPoolJpaRepository extends JpaRepository<CallerIdPoolEntity, Long> {

    Optional<CallerIdPoolEntity> findByIdAndCompanyId(Long id, Long companyId);

    List<CallerIdPoolEntity> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

    @Query("SELECT p FROM CallerIdPoolEntity p LEFT JOIN FETCH p.numbers WHERE p.id = :id AND p.companyId = :companyId")
    Optional<CallerIdPoolEntity> findByIdAndCompanyIdWithNumbers(Long id, Long companyId);
}
