package uz.murodjon.uysotvoice.engine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.engine.entity.EngineConfigEntity;

import java.util.Optional;

/** Spring Data repository for {@link EngineConfigEntity}. */
@Repository
public interface EngineConfigJpaRepository extends JpaRepository<EngineConfigEntity, Long> {

    Optional<EngineConfigEntity> findByCompanyId(long companyId);
}
