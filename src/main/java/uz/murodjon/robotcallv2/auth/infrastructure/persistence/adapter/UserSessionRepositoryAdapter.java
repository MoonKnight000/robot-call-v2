package uz.murodjon.robotcallv2.auth.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.auth.application.mapper.UserSessionMapper;
import uz.murodjon.robotcallv2.auth.application.port.output.UserSessionRepository;
import uz.murodjon.robotcallv2.auth.domain.entity.UserSession;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.entity.UserSessionEntity;
import uz.murodjon.robotcallv2.auth.infrastructure.persistence.repository.UserSessionJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class UserSessionRepositoryAdapter implements UserSessionRepository {

    private final UserSessionJpaRepository userSessionJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final UserSessionMapper mapper;

    public UserSessionRepositoryAdapter(UserSessionJpaRepository userSessionJpaRepository, UserJpaRepository userJpaRepository,
                                        CompanyJpaRepository companyJpaRepository, UserSessionMapper mapper) {
        this.userSessionJpaRepository = userSessionJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, long userId, String tokenHash, Instant expiresAt,
                       String device, String ipAddress) {
        UserEntity user = userJpaRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USER_NOT_FOUND, userId));
        CompanyEntity company = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        UserSessionEntity entity = new UserSessionEntity();
        entity.setCompany(company);
        entity.setUser(user);
        entity.setRefreshTokenHash(tokenHash);
        entity.setDevice(device);
        entity.setIpAddress(ipAddress);
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setLastActivityAt(now);
        entity.setExpiresAt(expiresAt);
        return userSessionJpaRepository.save(entity).getId();
    }

    @Override
    public Optional<UserSession> findActiveByHash(String tokenHash) {
        return userSessionJpaRepository.findByRefreshTokenHashAndRevokedAtIsNull(tokenHash).map(mapper::entityToDomain);
    }

    @Override
    public void rotate(long id, String newTokenHash, Instant newExpiresAt, String device, String ipAddress) {
        userSessionJpaRepository.findById(id).ifPresent(entity -> {
            entity.setRefreshTokenHash(newTokenHash);
            entity.setExpiresAt(newExpiresAt);
            entity.setLastActivityAt(Instant.now());
            if (device != null) {
                entity.setDevice(device);
            }
            if (ipAddress != null) {
                entity.setIpAddress(ipAddress);
            }
            userSessionJpaRepository.save(entity);
        });
    }

    @Override
    public void revoke(long id, long userId) {
        userSessionJpaRepository.findByIdAndUserId(id, userId).ifPresent(entity -> {
            entity.setRevokedAt(Instant.now());
            userSessionJpaRepository.save(entity);
        });
    }

    @Override
    public void revokeAllForUser(long userId) {
        userSessionJpaRepository.findByUserIdAndRevokedAtIsNullOrderByLastActivityAtDesc(userId)
                .forEach(entity -> {
                    entity.setRevokedAt(Instant.now());
                    userSessionJpaRepository.save(entity);
                });
    }

    @Override
    public List<UserSession> listActiveForUser(long userId) {
        return userSessionJpaRepository.findByUserIdAndRevokedAtIsNullOrderByLastActivityAtDesc(userId).stream()
                .map(mapper::entityToDomain)
                .toList();
    }
}
