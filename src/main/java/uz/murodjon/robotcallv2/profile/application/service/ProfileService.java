package uz.murodjon.robotcallv2.profile.application.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.auth.application.dto.TwoFactorSetupResponse;
import uz.murodjon.robotcallv2.auth.application.port.input.SessionUseCase;
import uz.murodjon.robotcallv2.auth.application.port.input.TwoFactorUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.profile.application.dto.*;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileUseCase;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;
import uz.murodjon.robotcallv2.report.application.port.input.DashboardReportUseCase;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardTotals;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.port.input.ImageUploadUseCase;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class ProfileService implements ProfileUseCase {

    private final UserUseCase userUseCase;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final ImageUploadUseCase imageUploadUseCase;
    private final AuditUseCase audit;
    private final DashboardReportUseCase dashboardReportUseCase;
    private final SessionUseCase sessionUseCase;
    private final TwoFactorUseCase twoFactor;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final Clock clock;

    public ProfileService(UserUseCase userUseCase, CurrentUser currentUser, PasswordEncoder passwordEncoder,
                          ImageUploadUseCase imageUploadUseCase, AuditUseCase audit, DashboardReportUseCase dashboardReportUseCase,
                          SessionUseCase sessionUseCase, TwoFactorUseCase twoFactor,
                          CompanyConfigUseCase companyConfigUseCase, Clock clock) {
        this.twoFactor = twoFactor;
        this.userUseCase = userUseCase;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.imageUploadUseCase = imageUploadUseCase;
        this.audit = audit;
        this.dashboardReportUseCase = dashboardReportUseCase;
        this.sessionUseCase = sessionUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.clock = clock;
    }

    @Override
    public Profile find(long companyId) {
        return toProfile(requireUser(companyId));
    }

    @Override
    public Profile update(long companyId, UpdateProfileRequest request) {
        User user = requireUser(companyId);
        if (!user.email().equalsIgnoreCase(request.email()) && userUseCase.existsByEmail(request.email())) {
            throw new ValidationException(ErrorCode.EMAIL_ALREADY_TAKEN);
        }
        userUseCase.updateProfile(companyId, user.id(), request.name(), request.email(), request.phone(), request.position(),
                user.sipExtension());
        audit.record(companyId, "PROFILE_UPDATE", "user", String.valueOf(user.id()), request.name());
        return find(companyId);
    }

    @Override
    public Profile uploadAvatar(long companyId, UploadedFile file) {
        User user = requireUser(companyId);
        StoredFile stored = imageUploadUseCase.upload(file, user.companyId());
        userUseCase.updateAvatarFileId(companyId, user.id(), stored.id());
        audit.record(companyId, "PROFILE_AVATAR_UPLOAD", "user", String.valueOf(user.id()), String.valueOf(stored.id()));
        return find(companyId);
    }

    @Override
    public void changePassword(long companyId, ChangePasswordRequest request) {
        User user = requireUser(companyId);
        if (user.passwordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ValidationException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        userUseCase.updatePassword(companyId, user.id(), passwordEncoder.encode(request.newPassword()));
        audit.record(companyId, "PROFILE_PASSWORD_CHANGE", "user", String.valueOf(user.id()), null);
        // A password is changed because the old one may be in the wrong hands — and so
        // may every session and token that was opened with it. This device logs in again.
        sessionUseCase.revokeAllForUser(user.id());
    }

    @Override
    public List<String> updateCallColumns(long companyId, UpdateCallColumnsRequest request) {
        User user = requireUser(companyId);
        userUseCase.updateCallColumns(companyId, user.id(), String.join(",", request.columns()));
        return request.columns();
    }

    @Override
    public TodayStats findTodayStats(long companyId) {
        long userId = requireUserId();
        // "Today" is the company's day — a UTC day would reset the operator's figures at 05:00 in Tashkent.
        CompanyConfig config = companyConfigUseCase.find(companyId);
        ZoneId zone = config != null ? config.zoneId() : clock.getZone();
        Instant now = clock.instant();
        Instant startOfToday = LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant();
        DashboardTotals totals = dashboardReportUseCase.findOperatorTotals(companyId, startOfToday, now, userId);
        double onAirMinutes = totals.avgDurationSec() == null
                ? 0
                : totals.avgDurationSec() * totals.answeredCalls() / 60.0;
        double qualityPct = totals.totalCalls() == 0 ? 0 : (double) totals.answeredCalls() / totals.totalCalls() * 100;
        return new TodayStats(totals.totalCalls(), totals.answeredCalls(), onAirMinutes, qualityPct);
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }

    private User requireUser(long companyId) {
        return userUseCase.requireUser(companyId, requireUserId());
    }

    private static Profile toProfile(User u) {
        return new Profile(u.id(), u.name(), u.username(), u.email(), u.phone(), u.position(),
                u.avatarFileId(), u.roleId(), u.roleCode(), u.roleName(), u.companyId(), u.lastLoginAt(), u.createdAt(),
                parseCallColumns(u.callColumns()));
    }

    private static List<String> parseCallColumns(String stored) {
        return stored == null || stored.isBlank() ? null : List.of(stored.split(","));
    }

    @Override
    public TwoFactorSetupResponse setupTwoFactor(long companyId) {
        User user = requireUser(companyId);
        return twoFactor.startTwoFactorSetup(companyId, user.id(), user.username());
    }

    @Override
    public void enableTwoFactor(long companyId, TwoFactorCodeRequest request) {
        twoFactor.enable(companyId, requireUser(companyId).id(), request.code());
    }

    /** Both factors: the password proves who is asking, the code proves they still hold the phone. */
    @Override
    public void disableTwoFactor(long companyId, DisableTwoFactorRequest request) {
        User user = requireUser(companyId);
        if (user.passwordHash() == null || !passwordEncoder.matches(request.currentPassword(), user.passwordHash())) {
            throw new ValidationException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        twoFactor.disable(companyId, user.id(), request.code());
    }
}
