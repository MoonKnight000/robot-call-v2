package uz.murodjon.robotcallv2.secret.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.secret.infrastructure.persistence.entity.SecretEntity;

import java.util.List;
import java.util.Optional;

public interface SecretJpaRepository extends JpaRepository<SecretEntity, Long> {

    List<SecretEntity> findAllByCompanyIdOrderByKeyAsc(long companyId);

    Optional<SecretEntity> findByCompanyIdAndId(long companyId, long id);

    boolean existsByCompanyIdAndKey(long companyId, String key);

    void deleteByCompanyIdAndId(long companyId, long id);
}
