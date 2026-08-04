package uz.murodjon.uysotvoice.user.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.user.dto.User;
import uz.murodjon.uysotvoice.user.entity.UserEntity;
import uz.murodjon.uysotvoice.user.enums.UserRole;
import uz.murodjon.uysotvoice.user.enums.UserStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * JPA-backed DAO for {@code app_user} (ROADMAP E.1). Company-scoped methods use
 * {@link CurrentCompany} like every other repository (ROADMAP B.2); {@link #findByEmail}
 * is deliberately unscoped — at login the company isn't known yet, the account lookup by
 * email is what determines it.
 */
@Repository
public class UserRepository {

    private final UserJpaRepository jpa;
    private final CurrentCompany company;

    public UserRepository(UserJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    /** Creates the account in the current company. Returns the new row's id. */
    public long create(String name, String username, String email, UserRole role, UserStatus status) {
        UserEntity entity = new UserEntity();
        entity.setCompanyId(company.id());
        entity.setName(name);
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setRole(role);
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Same as {@link #create}, but for an explicit company — used by the startup bootstrap. */
    public long createForCompany(long companyId, String name, String username, String email, String passwordHash,
                                  UserRole role, UserStatus status) {
        UserEntity entity = new UserEntity();
        entity.setCompanyId(companyId);
        entity.setName(name);
        entity.setUsername(username);
        entity.setEmail(email);
        entity.setPasswordHash(passwordHash);
        entity.setRole(role);
        entity.setStatus(status);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    public boolean existsByUsername(String username) {
        return jpa.existsByUsername(username);
    }

    /** Unscoped by design — see class javadoc. */
    public Optional<UserEntity> findByEmail(String email) {
        return jpa.findByEmail(email);
    }

    /** Unscoped by design, same reasoning as {@link #findByEmail} — login happens by username now. */
    public Optional<UserEntity> findByUsername(String username) {
        return jpa.findByUsername(username);
    }

    /** Unscoped by design — {@code POST /api/auth/refresh} only knows the user id off a {@code user_session} row. */
    public Optional<UserEntity> findById(long id) {
        return jpa.findById(id);
    }

    public User find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(UserRepository::toRow).orElse(null);
    }

    /** Full entity (incl. profile fields not on the {@link User} row) — {@code profile.service.ProfileService}. */
    public Optional<UserEntity> findEntity(long id) {
        return jpa.findByIdAndCompanyId(id, company.id());
    }

    /**
     * §15 "Bugun" mini-statistika — {@code AriService.handleOperatorJoin} resolves the
     * PJSIP endpoint that answered a transfer back to whichever user registered it as
     * their own ({@code PUT /api/profile}). Explicit {@code companyId} (not {@link
     * CurrentCompany}), same reasoning as {@link #findActiveByCompany} — this runs from
     * a live call, not a request scoped to the operator's own session.
     */
    public User findBySipExtension(String sipExtension, long companyId) {
        if (sipExtension == null || sipExtension.isBlank()) {
            return null;
        }
        return jpa.findBySipExtensionAndCompanyId(sipExtension, companyId).map(UserRepository::toRow).orElse(null);
    }

    public List<User> findAll() {
        return jpa.findByCompanyIdOrderById(company.id()).stream().map(UserRepository::toRow).toList();
    }

    /** Cheap id→name lookup for other features to enrich rows with e.g. {@code createdByName}. */
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpa.findNamesByIds(ids, company.id()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    /**
     * Every active user of an explicit company, unscoped by {@link CurrentCompany} — for
     * {@code notification.service.NotificationService}'s fan-out, which notifies a
     * specific company (the one the triggering event happened in), not necessarily the
     * one the current request/thread is scoped to. Mirrors {@code CampaignRepository#findActive}.
     */
    public List<User> findActiveByCompany(long companyId) {
        return jpa.findByCompanyIdOrderById(companyId).stream()
                .filter(e -> e.getStatus() == UserStatus.ACTIVE)
                .map(UserRepository::toRow)
                .toList();
    }

    public boolean hasAnyUser(long companyId) {
        return jpa.existsByCompanyId(companyId);
    }

    public long countActiveAdmins(long companyId) {
        return jpa.countByCompanyIdAndRoleAndStatus(companyId, UserRole.ADMIN, UserStatus.ACTIVE);
    }

    public void setInviteToken(long id, String tokenHash, Instant expiresAt) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setInviteTokenHash(tokenHash);
            entity.setInviteExpiresAt(expiresAt);
            jpa.save(entity);
        });
    }

    public Optional<UserEntity> findByInviteTokenHash(String tokenHash) {
        return jpa.findByInviteTokenHash(tokenHash);
    }

    /** Sets the password, clears the invite token and marks the account {@link UserStatus#ACTIVE}. */
    public void activate(long id, String passwordHash) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            entity.setStatus(UserStatus.ACTIVE);
            entity.setInviteTokenHash(null);
            entity.setInviteExpiresAt(null);
            jpa.save(entity);
        });
    }

    /** {@code POST /api/auth/forgot-password} — same one-time-token pattern as {@link #setInviteToken}. */
    public void setResetToken(long id, String tokenHash, Instant expiresAt) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setResetTokenHash(tokenHash);
            entity.setResetExpiresAt(expiresAt);
            jpa.save(entity);
        });
    }

    /** Unscoped by design, same reasoning as {@link #findByInviteTokenHash} — the token itself is the proof. */
    public Optional<UserEntity> findByResetTokenHash(String tokenHash) {
        return jpa.findByResetTokenHash(tokenHash);
    }

    /** {@code POST /api/auth/reset-password} — sets the password and clears the reset token (one-time use). */
    public void resetPassword(long id, String passwordHash) {
        jpa.findById(id).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            entity.setResetTokenHash(null);
            entity.setResetExpiresAt(null);
            jpa.save(entity);
        });
    }

    public void updateRole(long id, UserRole role) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setRole(role);
            jpa.save(entity);
        });
    }

    public void updateStatus(long id, UserStatus status) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setStatus(status);
            jpa.save(entity);
        });
    }

    public void touchLastLogin(long id) {
        jpa.touchLastLogin(id, Instant.now());
    }

    /** {@code PUT /api/profile} — self-service "Umumiy" tab (API-REQUIREMENTS §15). */
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

    /** {@code POST /api/profile/avatar} — a partial update, leaves every other profile field untouched. */
    public void updateAvatarFileId(long id, Long avatarFileId) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setAvatarFileId(avatarFileId);
            jpa.save(entity);
        });
    }

    /** {@code PUT /api/profile/password} — self-service "Xavfsizlik" tab. */
    public void updatePassword(long id, String passwordHash) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setPasswordHash(passwordHash);
            jpa.save(entity);
        });
    }

    /** {@code PUT /api/profile/call-columns} — persisted "Ustunlar ⚙" choice (API-REQUIREMENTS §4). */
    public void updateCallColumns(long id, String callColumns) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setCallColumns(callColumns);
            jpa.save(entity);
        });
    }

    private static User toRow(UserEntity e) {
        return new User(e.getId(), e.getCompanyId(), e.getName(), e.getUsername(), e.getEmail(), e.getRole(),
                e.getStatus(), e.getLastLoginAt(), e.getCreatedAt());
    }
}
