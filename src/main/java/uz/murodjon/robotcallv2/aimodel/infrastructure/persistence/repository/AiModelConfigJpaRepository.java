package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity.AiModelConfigEntity;

import java.util.Optional;

@Repository
public interface AiModelConfigJpaRepository extends JpaRepository<AiModelConfigEntity, Long> {

    Optional<AiModelConfigEntity> findByCompanyId(long companyId);
}
