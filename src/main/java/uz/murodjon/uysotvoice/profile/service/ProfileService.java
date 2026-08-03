package uz.murodjon.uysotvoice.profile.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.profile.dto.ChangePasswordRequest;
import uz.murodjon.uysotvoice.profile.dto.Profile;
import uz.murodjon.uysotvoice.profile.dto.TodayStats;
import uz.murodjon.uysotvoice.profile.dto.UpdateCallColumnsRequest;
import uz.murodjon.uysotvoice.profile.dto.UpdateProfileRequest;
import uz.murodjon.uysotvoice.report.dto.DashboardTotals;
import uz.murodjon.uysotvoice.report.repository.ReportRepository;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.user.entity.UserEntity;
import uz.murodjon.uysotvoice.user.repository.UserRepository;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Self-service "Umumiy"/"Xavfsizlik" profile tabs (API-REQUIREMENTS §15, UI-DESIGN §8.3)
 * — always acts on the caller's own {@code app_user} row, never another user's.
 */
@Service
public class ProfileService {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final ReportRepository reports;

    public ProfileService(UserRepository users, CurrentUser currentUser, PasswordEncoder passwordEncoder,
                           AuditService audit, ReportRepository reports) {
        this.users = users;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.reports = reports;
    }

    public Profile find() {
        return toProfile(requireEntity());
    }

    public Profile update(UpdateProfileRequest r) {
        UserEntity entity = requireEntity();
        if (!entity.getEmail().equalsIgnoreCase(r.email()) && users.existsByEmail(r.email())) {
            throw new ValidationException("bu email allaqachon band");
        }
        users.updateProfile(entity.getId(), r.name(), r.email(), r.phone(), r.position(), r.avatarUrl(),
                entity.getSipExtension());
        audit.record("PROFILE_UPDATE", "user", String.valueOf(entity.getId()), r.name());
        return find();
    }

    public void changePassword(ChangePasswordRequest r) {
        UserEntity entity = requireEntity();
        if (entity.getPasswordHash() == null || !passwordEncoder.matches(r.currentPassword(), entity.getPasswordHash())) {
            throw new ValidationException("joriy parol noto'g'ri");
        }
        users.updatePassword(entity.getId(), passwordEncoder.encode(r.newPassword()));
        audit.record("PROFILE_PASSWORD_CHANGE", "user", String.valueOf(entity.getId()), null);
    }

    public List<String> updateCallColumns(UpdateCallColumnsRequest r) {
        UserEntity entity = requireEntity();
        users.updateCallColumns(entity.getId(), String.join(",", r.columns()));
        return r.columns();
    }

    /**
     * Operator-scoped (backend-uchun-talablar.md §6) — only calls transferred to and
     * answered by this operator's own SIP extension count, via {@link
     * ReportRepository#operatorTotals}.
     */
    public TodayStats todayStats() {
        long userId = requireUserId();
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);
        DashboardTotals totals = reports.operatorTotals(startOfToday, Instant.now(), userId);
        double onAirMinutes = totals.avgDurationSec() == null
                ? 0
                : totals.avgDurationSec() * totals.answeredCalls() / 60.0;
        double qualityPct = totals.totalCalls() == 0 ? 0 : (double) totals.answeredCalls() / totals.totalCalls() * 100;
        return new TodayStats(totals.totalCalls(), totals.answeredCalls(), onAirMinutes, qualityPct);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException("no user session on this request"));
    }

    private UserEntity requireEntity() {
        long id = requireUserId();
        return users.findEntity(id).orElseThrow(() -> new NotFoundException("user", id));
    }

    private static Profile toProfile(UserEntity e) {
        return new Profile(e.getId(), e.getName(), e.getUsername(), e.getEmail(), e.getPhone(), e.getPosition(),
                e.getAvatarUrl(), e.getRole(), e.getCompanyId(), e.getLastLoginAt(), e.getCreatedAt(),
                parseCallColumns(e.getCallColumns()));
    }

    private static List<String> parseCallColumns(String stored) {
        return stored == null || stored.isBlank() ? null : List.of(stored.split(","));
    }
}
