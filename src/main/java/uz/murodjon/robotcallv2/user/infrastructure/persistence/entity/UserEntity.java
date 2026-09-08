package uz.murodjon.robotcallv2.user.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.role.infrastructure.persistence.entity.RoleEntity;
import uz.murodjon.robotcallv2.storage.infrastructure.persistence.entity.StoredFileEntity;
import uz.murodjon.robotcallv2.user.domain.enums.UserStatus;

import java.time.Instant;

/** JPA entity for app_user (ROADMAP E.1). */
@Entity
@Table(name = "app_user")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private CompanyEntity company;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    // Eager: UserMapper reads the role name and code outside any transaction, where a lazy
    // proxy would fail, and a company has at most a handful of roles.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private RoleEntity role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "invite_token_hash")
    private String inviteTokenHash;

    @Column(name = "invite_expires_at")
    private Instant inviteExpiresAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "phone")
    private String phone;

    @Column(name = "position")
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_file_id")
    private StoredFileEntity avatarFile;

    @Column(name = "call_columns")
    private String callColumns;

    @Column(name = "sip_extension")
    private String sipExtension;

    @Column(name = "reset_token_hash")
    private String resetTokenHash;

    @Column(name = "reset_expires_at")
    private Instant resetExpiresAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CompanyEntity getCompany() {
        return company;
    }

    public void setCompany(CompanyEntity company) {
        this.company = company;
    }

    public long getCompanyId() {
        return company != null ? company.getId() : 0L;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public RoleEntity getRole() {
        return role;
    }

    public void setRole(RoleEntity role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public String getInviteTokenHash() {
        return inviteTokenHash;
    }

    public void setInviteTokenHash(String inviteTokenHash) {
        this.inviteTokenHash = inviteTokenHash;
    }

    public Instant getInviteExpiresAt() {
        return inviteExpiresAt;
    }

    public void setInviteExpiresAt(Instant inviteExpiresAt) {
        this.inviteExpiresAt = inviteExpiresAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public StoredFileEntity getAvatarFile() {
        return avatarFile;
    }

    public void setAvatarFile(StoredFileEntity avatarFile) {
        this.avatarFile = avatarFile;
    }

    public Long getAvatarFileId() {
        return avatarFile != null ? avatarFile.getId() : null;
    }

    public String getCallColumns() {
        return callColumns;
    }

    public void setCallColumns(String callColumns) {
        this.callColumns = callColumns;
    }

    public String getSipExtension() {
        return sipExtension;
    }

    public void setSipExtension(String sipExtension) {
        this.sipExtension = sipExtension;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public void setResetTokenHash(String resetTokenHash) {
        this.resetTokenHash = resetTokenHash;
    }

    public Instant getResetExpiresAt() {
        return resetExpiresAt;
    }

    public void setResetExpiresAt(Instant resetExpiresAt) {
        this.resetExpiresAt = resetExpiresAt;
    }
}
