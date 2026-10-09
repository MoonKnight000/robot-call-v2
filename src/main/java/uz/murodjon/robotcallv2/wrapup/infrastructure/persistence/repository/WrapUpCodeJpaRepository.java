package uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.wrapup.infrastructure.persistence.entity.WrapUpCodeEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface WrapUpCodeJpaRepository extends JpaRepository<WrapUpCodeEntity, Long> {

    Optional<WrapUpCodeEntity> findByIdAndCompanyId(long id, long companyId);

    List<WrapUpCodeEntity> findByCompanyId(long companyId, Pageable pageable);

    List<WrapUpCodeEntity> findByCompanyIdAndEnabled(long companyId, boolean enabled, Pageable pageable);

    long countByCompanyId(long companyId);

    long countByCompanyIdAndEnabled(long companyId, boolean enabled);
}
