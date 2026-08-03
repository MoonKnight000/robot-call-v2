package uz.murodjon.uysotvoice.aimodel.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.aimodel.entity.AiModelConfigEntity;

import java.util.Optional;

/** Spring Data repository for {@link AiModelConfigEntity}. */
@Repository
public interface AiModelConfigJpaRepository extends JpaRepository<AiModelConfigEntity, Long> {

    Optional<AiModelConfigEntity> findByCompanyId(long companyId);
}
