package uz.murodjon.robotcallv2.engine.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.engine.infrastructure.persistence.entity.EngineConfigEntity;

import java.util.Optional;

@Repository
public interface EngineConfigJpaRepository extends JpaRepository<EngineConfigEntity, Long> {

    Optional<EngineConfigEntity> findByCompanyId(long companyId);
}
