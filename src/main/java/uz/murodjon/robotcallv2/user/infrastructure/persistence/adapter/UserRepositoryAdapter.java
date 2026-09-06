package uz.murodjon.robotcallv2.user.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.mapper.UserMapper;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.domain.entity.User;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.repository.RoleJpaRepository;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.entity.UserEntity;
import uz.murodjon.robotcallv2.user.infrastructure.persistence.repository.UserJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository userJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final RoleJpaRepository roleJpaRepository;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository userJpaRepository, CompanyJpaRepository companyJpaRepository,
                                 RoleJpaRepository roleJpaRepository,
                                 UserMapper mapper) {
        this.userJpaRepository = userJpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.roleJpaRepository = roleJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, String name, String username, String email, String passwordHash,
                                  long roleId, UserStatus status) {
        CompanyEntity comp = companyJpaRepository.findById(companyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.COMPANY_NOT_FOUND, companyId));

        UserEntity entity = new UserEntity();
        entity.setCompany(comp);
        entity.setName(name);
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setPasswordHash(passwordHash);
        entity.setRole(roleReference(roleId));
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        return userJpaRepository.save(entity).getId();
    }

    @Override
    public boolean existsByEmail(String email) {
        return userJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userJpaRepository.existsByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userJpaRepository.findByEmail(email).map(mapper::entityToDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userJpaRepository.findByUsername(username).map(mapper::entityToDomain);
    }

    @Override
    public Optional<User> findById(long id) {
        return userJpaRepository.findById(id).map(mapper::entityToDomain);
    }

    @Override
    public User find(long companyId, long id) {
        return userJpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public User findBySipExtension(String sipExtension, long companyId) {
        if (sipExtension == null || sipExtension.isBlank()) {
            return null;
        }
        return userJpaRepository.findBySipExtensionAndCompanyId(sipExtension, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<User> findAll(long companyId) {
        return userJpaRepository.findByCompanyIdOrderById(companyId).stream().map(mapper::entityToDomain).toList();
    }

    @Override
    public Map<Long, String> namesByIds(long companyId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userJpaRepository.findNamesByIds(ids, companyId).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    @Override
    public List<User> findActiveByCompany(long companyId) {
        return userJpaRepository.findByCompanyIdOrderById(companyId).stream()
                .filter(e -> e.getStatus() == UserStatus.ACTIVE)
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public boolean hasAnyUser(long companyId) {
        return userJpaRepository.existsByCompanyId(companyId);
    }

    @Override
    public long countActiveByRoleIds(long companyId, Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return 0;
        }
        return userJpaRepository.countByCompanyIdAndRoleIdsAndStatus(companyId, roleIds, UserStatus.ACTIVE);
    }

    @Override
    public void setInviteToken(long id, String tokenHash, Instant expiresAt) {
        userJpaRepository.findById(id).ifPresent(entity -> {
            entity.setInviteTokenHash(tokenHash);
            entity.setInviteExpiresAt(expiresAt);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public Optional<User> findByInviteTokenHash(String tokenHash) {
        return userJpaRepository.findByInviteTokenHash(tokenHash).map(mapper::entityToDomain);
    }

    @Override
    public void activate(long id, String passwordHash) {
        userJpaRepository.findById(id).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            entity.setStatus(UserStatus.ACTIVE);
            entity.setInviteTokenHash(null);
            entity.setInviteExpiresAt(null);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public void setResetToken(long id, String tokenHash, Instant expiresAt) {
        userJpaRepository.findById(id).ifPresent(entity -> {
            entity.setResetTokenHash(tokenHash);
            entity.setResetExpiresAt(expiresAt);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public Optional<User> findByResetTokenHash(String tokenHash) {
        return userJpaRepository.findByResetTokenHash(tokenHash).map(mapper::entityToDomain);
    }

    @Override
    public void resetPassword(long id, String passwordHash) {
        userJpaRepository.findById(id).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            entity.setResetTokenHash(null);
            entity.setResetExpiresAt(null);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public void updateRole(long companyId, long id, long roleId) {
        userJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setRole(roleReference(roleId));
            userJpaRepository.save(entity);
        });
    }

    @Override
    public void updateStatus(long companyId, long id, UserStatus status) {
        userJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setStatus(status);
            userJpaRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void touchLastLogin(long id) {
        userJpaRepository.touchLastLogin(id, Instant.now());
    }

    @Override
    public void updateProfile(long companyId, long id, String name, String email, String phone, String position,
                              String sipExtension) {
        userJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setName(name);
            entity.setEmail(email);
            entity.setPhone(phone);
            entity.setPosition(position);
            entity.setSipExtension(sipExtension);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public void updateAvatarFileId(long companyId, long id, Long avatarFileId) {
        userJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setAvatarFileId(avatarFileId);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public void updatePassword(long companyId, long id, String passwordHash) {
        userJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            userJpaRepository.save(entity);
        });
    }

    @Override
    public void updateCallColumns(long companyId, long id, String callColumns) {
        userJpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setCallColumns(callColumns);
            userJpaRepository.save(entity);
        });
    }

    private RoleEntity roleReference(long roleId) {
        return roleJpaRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND, roleId));
    }
}
