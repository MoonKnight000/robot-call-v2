package uz.murodjon.robotcallv2.auth.infrastructure.persistence.adapter;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.auth.application.port.output.UserTotpRepository;
import uz.murodjon.robotcallv2.auth.domain.entity.UserTotp;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity.UserTotpEntity;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.repository.UserTotpJpaRepository;

import java.time.Instant;

@Repository
public class UserTotpRepositoryAdapter implements UserTotpRepository {

    private final UserTotpJpaRepository jpaRepository;

    public UserTotpRepositoryAdapter(UserTotpJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public UserTotp find(long userId) {
        return jpaRepository.findById(userId)
                .map(entity -> new UserTotp(entity.getUserId(), entity.getCompanyId(), entity.getSecretEncrypted(),
                        entity.isEnabled(), entity.getLastUsedStep(), entity.getEnabledAt()))
                .orElse(null);
    }

    @Override
    @Transactional
    public void save(UserTotp totp) {
        UserTotpEntity entity = jpaRepository.findById(totp.userId()).orElseGet(() -> {
            UserTotpEntity created = new UserTotpEntity();
            created.setUserId(totp.userId());
            created.setCreatedAt(Instant.now());
            return created;
        });
        entity.setCompanyId(totp.companyId());
        entity.setSecretEncrypted(totp.secretEncrypted());
        entity.setEnabled(totp.enabled());
        entity.setLastUsedStep(totp.lastUsedStep());
        entity.setEnabledAt(totp.enabledAt());
        jpaRepository.save(entity);
    }

    @Override
    public boolean markUsed(long userId, long step) {
        return jpaRepository.markUsed(userId, step) == 1;
    }

    @Override
    @Transactional
    public void delete(long userId) {
        jpaRepository.deleteById(userId);
    }
}
