package uz.murodjon.robotcallv2.operator.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.operator.infrastructure.persistence.entity.OperatorSipAccountEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface OperatorSipAccountJpaRepository extends JpaRepository<OperatorSipAccountEntity, Long> {

    Optional<OperatorSipAccountEntity> findByUserIdAndCompanyId(long userId, long companyId);

    List<OperatorSipAccountEntity> findByCompanyId(long companyId);
}
