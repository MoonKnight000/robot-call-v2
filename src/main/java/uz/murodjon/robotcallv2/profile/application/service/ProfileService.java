package uz.murodjon.robotcallv2.profile.application.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.profile.application.dto.ChangePasswordRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateCallColumnsRequest;
import uz.murodjon.robotcallv2.profile.application.dto.UpdateProfileRequest;
import uz.murodjon.robotcallv2.profile.application.port.input.ProfileUseCase;
import uz.murodjon.robotcallv2.profile.domain.entity.Profile;
import uz.murodjon.robotcallv2.profile.domain.entity.TodayStats;
import uz.murodjon.robotcallv2.report.application.port.output.ReportRepository;
import uz.murodjon.robotcallv2.report.domain.entity.DashboardTotals;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.storage.application.service.ImageUploadService;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.user.application.port.output.UserRepository;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.domain.entity.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class ProfileService implements ProfileUseCase {

    private final UserRepository users;
    private final CurrentUser currentUser;
    private final PasswordEncoder passwordEncoder;
    private final ImageUploadService images;
    private final AuditService audit;
    private final ReportRepository reports;

    public ProfileService(UserRepository users, CurrentUser currentUser, PasswordEncoder passwordEncoder,
                          ImageUploadService images, AuditService audit, ReportRepository reports) {
        this.users = users;
        this.currentUser = currentUser;
        this.passwordEncoder = passwordEncoder;
        this.images = images;
        this.audit = audit;
        this.reports = reports;
    }

    @Override
    public Profile find() {
        return toProfile(requireUser());
    }

    @Override
    public Profile update(UpdateProfileRequest r) {
        User user = requireUser();
        if (!user.email().equalsIgnoreCase(r.email()) && users.existsByEmail(r.email())) {
            throw new ValidationException(ErrorCode.EMAIL_ALREADY_TAKEN);
        }
        users.updateProfile(user.id(), r.name(), r.email(), r.phone(), r.position(), user.sipExtension());
        audit.record("PROFILE_UPDATE", "user", String.valueOf(user.id()), r.name());
        return find();
    }

    @Override
    public Profile uploadAvatar(MultipartFile file) {
        User user = requireUser();
        StoredFile stored = images.upload(file, user.companyId());
        users.updateAvatarFileId(user.id(), stored.id());
        audit.record("PROFILE_AVATAR_UPLOAD", "user", String.valueOf(user.id()), String.valueOf(stored.id()));
        return find();
    }

    @Override
    public void changePassword(ChangePasswordRequest r) {
        User user = requireUser();
        if (user.passwordHash() == null || !passwordEncoder.matches(r.currentPassword(), user.passwordHash())) {
            throw new ValidationException(ErrorCode.CURRENT_PASSWORD_INCORRECT);
        }
        users.updatePassword(user.id(), passwordEncoder.encode(r.newPassword()));
        audit.record("PROFILE_PASSWORD_CHANGE", "user", String.valueOf(user.id()), null);
    }

    @Override
    public List<String> updateCallColumns(UpdateCallColumnsRequest r) {
        User user = requireUser();
        users.updateCallColumns(user.id(), String.join(",", r.columns()));
        return r.columns();
    }

    @Override
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
        return currentUser.id().orElseThrow(() -> new ForbiddenException(ErrorCode.NO_USER_SESSION));
    }

    private User requireUser() {
        long id = requireUserId();
        User user = users.find(id);
        if (user == null) {
            throw new NotFoundException(ErrorCode.USER_NOT_FOUND, id);
        }
        return user;
    }

    private static Profile toProfile(User u) {
        return new Profile(u.id(), u.name(), u.username(), u.email(), u.phone(), u.position(),
                u.avatarFileId(), u.role(), u.companyId(), u.lastLoginAt(), u.createdAt(),
                parseCallColumns(u.callColumns()));
    }

    private static List<String> parseCallColumns(String stored) {
        return stored == null || stored.isBlank() ? null : List.of(stored.split(","));
    }
}

