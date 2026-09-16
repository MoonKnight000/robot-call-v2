package uz.murodjon.robotcallv2.callflow.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.callflow.infrastructure.persistence.entity.CallFlowEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface CallFlowJpaRepository extends JpaRepository<CallFlowEntity, Long> {

    Optional<CallFlowEntity> findByIdAndCompanyId(long id, long companyId);

    List<CallFlowEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);
}
