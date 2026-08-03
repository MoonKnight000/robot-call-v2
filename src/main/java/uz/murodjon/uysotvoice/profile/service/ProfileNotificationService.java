package uz.murodjon.uysotvoice.profile.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.profile.dto.PersonalNotificationMatrixEntry;
import uz.murodjon.uysotvoice.profile.dto.UpdatePersonalNotificationSettingsRequest;
import uz.murodjon.uysotvoice.profile.repository.PersonalNotificationMatrixRepository;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.user.service.CurrentUser;

import java.util.List;

/**
 * Self-service "Bildirishnomalar" tab — personal channel x event matrix
 * (API-REQUIREMENTS §15), distinct from the company-wide one at {@code
 * notification.service.NotificationSettingsService} (§11 settings, ADMIN-only).
 */
@Service
public class ProfileNotificationService {

    private final PersonalNotificationMatrixRepository repo;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public ProfileNotificationService(PersonalNotificationMatrixRepository repo, CurrentUser currentUser,
                                       AuditService audit) {
        this.repo = repo;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    public List<PersonalNotificationMatrixEntry> find() {
        return repo.find(requireUserId());
    }

    public List<PersonalNotificationMatrixEntry> update(UpdatePersonalNotificationSettingsRequest r) {
        long userId = requireUserId();
        List<PersonalNotificationMatrixEntry> saved = repo.save(userId, r.matrix());
        audit.record("PROFILE_NOTIFICATIONS_UPDATE", "app_user", String.valueOf(userId),
                saved.size() + " matrix cell(s)");
        return saved;
    }

    private long requireUserId() {
        return currentUser.id().orElseThrow(() -> new ForbiddenException("no user session on this request"));
    }
}
