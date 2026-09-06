package uz.murodjon.robotcallv2.user.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
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

    private final UserJpaRepository jpa;
    private final CompanyJpaRepository companyJpa;
    private final RoleJpaRepository roleJpaRepository;
    private final CurrentCompany company;
    private final UserMapper mapper;

    public UserRepositoryAdapter(UserJpaRepository jpa, CompanyJpaRepository companyJpa,
                                 RoleJpaRepository roleJpaRepository, CurrentCompany company,
                                 UserMapper mapper) {
        this.jpa = jpa;
        this.companyJpa = companyJpa;
        this.roleJpaRepository = roleJpaRepository;
        this.company = company;
        this.mapper = mapper;
    }

    @Override
    public long create(String name, String username, String email, long roleId, UserStatus status) {
        return createForCompany(company.id(), name, username, email, null, roleId, status);
    }

    @Override
    public long createForCompany(long companyId, String name, String username, String email, String passwordHash,
                                  long roleId, UserStatus status) {
        CompanyEntity comp = companyJpa.findById(companyId)
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
        return jpa.save(entity).getId();
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email).map(mapper::entityToDomain);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpa.findByUsername(username).map(mapper::entityToDomain);
    }

    @Override
    public Optional<User> findById(long id) {
        return jpa.findById(id).map(mapper::entityToDomain);
    }

    @Override
    public User find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public User findBySipExtension(String sipExtension, long companyId) {
        if (sipExtension == null || sipExtension.isBlank()) {
            return null;
        }
        return jpa.findBySipExtensionAndCompanyId(sipExtension, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<User> findAll() {
        return jpa.findByCompanyIdOrderById(company.id()).stream().map(mapper::entityToDomain).toList();
    }

    @Override
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpa.findNamesByIds(ids, company.id()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    @Override
    public List<User> findActiveByCompany(long companyId) {
        return jpa.findByCompanyIdOrderById(companyId).stream()
                .filter(e -> e.getStatus() == UserStatus.ACTIVE)
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public boolean hasAnyUser(long companyId) {
        return jpa.existsByCompanyId(companyId);
    }

    @Override
    public long countActiveByRoleIds(long companyId, Collection<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return 0;
        }
        return jpa.countByCompanyIdAndRoleIdsAndStatus(companyId, roleIds, UserStatus.ACTIVE);
    }

    @Override
    public void setInviteToken(long id, String tokenHash, Instant expiresAt) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setInviteTokenHash(tokenHash);
            entity.setInviteExpiresAt(expiresAt);
            jpa.save(entity);
        });
    }

    @Override
    public Optional<User> findByInviteTokenHash(String tokenHash) {
        return jpa.findByInviteTokenHash(tokenHash).map(mapper::entityToDomain);
    }

    @Override
    public void activate(long id, String passwordHash) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            entity.setStatus(UserStatus.ACTIVE);
            entity.setInviteTokenHash(null);
            entity.setInviteExpiresAt(null);
            jpa.save(entity);
        });
    }

    @Override
    public void setResetToken(long id, String tokenHash, Instant expiresAt) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setResetTokenHash(tokenHash);
            entity.setResetExpiresAt(expiresAt);
            jpa.save(entity);
        });
    }

    @Override
    public Optional<User> findByResetTokenHash(String tokenHash) {
        return jpa.findByResetTokenHash(tokenHash).map(mapper::entityToDomain);
    }

    @Override
    public void resetPassword(long id, String passwordHash) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            entity.setResetTokenHash(null);
            entity.setResetExpiresAt(null);
            jpa.save(entity);
        });
    }

    @Override
    public void updateRole(long id, long roleId) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setRole(roleReference(roleId));
            jpa.save(entity);
        });
    }

    @Override
    public void updateStatus(long id, UserStatus status) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setStatus(status);
            jpa.save(entity);
        });
    }

    @Override
    @Transactional
    public void touchLastLogin(long id) {
        jpa.touchLastLogin(id, Instant.now());
    }

    @Override
    public void updateProfile(long id, String name, String email, String phone, String position,
                              String sipExtension) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setName(name);
            entity.setEmail(email);
            entity.setPhone(phone);
            entity.setPosition(position);
            entity.setSipExtension(sipExtension);
            jpa.save(entity);
        });
    }

    @Override
    public void updateAvatarFileId(long id, Long avatarFileId) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setAvatarFileId(avatarFileId);
            jpa.save(entity);
        });
    }

    @Override
    public void updatePassword(long id, String passwordHash) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            jpa.save(entity);
        });
    }

    @Override
    public void updateCallColumns(long id, String callColumns) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setCallColumns(callColumns);
            jpa.save(entity);
        });
    }

    private RoleEntity roleReference(long roleId) {
        return roleJpaRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ROLE_NOT_FOUND, roleId));
    }
}
