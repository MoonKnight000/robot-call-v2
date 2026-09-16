package uz.murodjon.robotcallv2.operator.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.operator.application.port.output.OperatorSipAccountRepository;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipAccount;
import uz.murodjon.robotcallv2.operator.infrastructure.persistence.entity.OperatorSipAccountEntity;
import uz.murodjon.robotcallv2.operator.infrastructure.persistence.repository.OperatorSipAccountJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class OperatorSipAccountRepositoryAdapter implements OperatorSipAccountRepository {

    private final OperatorSipAccountJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;

    public OperatorSipAccountRepositoryAdapter(OperatorSipAccountJpaRepository jpaRepository,
                                               CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public void create(long companyId, OperatorSipAccount account) {
        OperatorSipAccountEntity entity = new OperatorSipAccountEntity();
        entity.setUserId(account.userId());
        entity.setCompany(companyJpaRepository.getReferenceById(companyId));
        entity.setEndpointName(account.endpointName());
        entity.setPasswordEnc(account.passwordEnc());
        entity.setWebrtc(account.webrtc());
        entity.setForwardNumber(account.forwardNumber());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        jpaRepository.save(entity);
    }

    @Override
    public void update(long companyId, long userId, boolean webrtc, String forwardNumber) {
        jpaRepository.findByUserIdAndCompanyId(userId, companyId).ifPresent(entity -> {
            entity.setWebrtc(webrtc);
            entity.setForwardNumber(forwardNumber);
            entity.setUpdatedAt(Instant.now());
            jpaRepository.save(entity);
        });
    }

    @Override
    public void updatePassword(long companyId, long userId, String passwordEnc) {
        jpaRepository.findByUserIdAndCompanyId(userId, companyId).ifPresent(entity -> {
            entity.setPasswordEnc(passwordEnc);
            entity.setUpdatedAt(Instant.now());
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long userId) {
        jpaRepository.findByUserIdAndCompanyId(userId, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public OperatorSipAccount find(long companyId, long userId) {
        return jpaRepository.findByUserIdAndCompanyId(userId, companyId).map(this::toOperatorSipAccount).orElse(null);
    }

    @Override
    public OperatorSipAccount findByUserId(long userId) {
        return jpaRepository.findById(userId).map(this::toOperatorSipAccount).orElse(null);
    }

    @Override
    public List<OperatorSipAccount> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).stream().map(this::toOperatorSipAccount).toList();
    }

    @Override
    public List<OperatorSipAccount> findAll() {
        return jpaRepository.findAll().stream().map(this::toOperatorSipAccount).toList();
    }

    private OperatorSipAccount toOperatorSipAccount(OperatorSipAccountEntity entity) {
        return new OperatorSipAccount(entity.getUserId(), entity.getCompanyId(), entity.getEndpointName(),
                entity.getPasswordEnc(), entity.isWebrtc(), entity.getForwardNumber(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
