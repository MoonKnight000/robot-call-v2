package uz.murodjon.robotcallv2.schedule.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.schedule.infrastructure.persistence.entity.BusinessScheduleEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface BusinessScheduleJpaRepository extends JpaRepository<BusinessScheduleEntity, Long> {

    Optional<BusinessScheduleEntity> findByIdAndCompanyId(long id, long companyId);

    List<BusinessScheduleEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);
}
