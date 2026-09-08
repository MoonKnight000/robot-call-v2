package uz.murodjon.robotcallv2.secret.application.port.output;

import uz.murodjon.robotcallv2.secret.domain.entity.Secret;

import java.util.List;
import java.util.Optional;

public interface SecretRepository {

    Secret save(Secret secret);

    Optional<Secret> findByCompanyIdAndId(long companyId, long id);

    List<Secret> findByCompanyId(long companyId);

    boolean existsByCompanyIdAndKey(long companyId, String key);

    void deleteByCompanyIdAndId(long companyId, long id);
}
