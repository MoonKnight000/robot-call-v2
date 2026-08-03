package uz.murodjon.uysotvoice.notification.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.notification.dto.NotificationSettings;
import uz.murodjon.uysotvoice.notification.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.uysotvoice.notification.repository.NotificationSettingsRepository;

/** Company-level notification channel x event matrix CRUD (§11 settings). */
@Service
public class NotificationSettingsService {

    private final NotificationSettingsRepository repo;
    private final AuditService audit;

    public NotificationSettingsService(NotificationSettingsRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public NotificationSettings find() {
        return repo.find();
    }

    public NotificationSettings update(UpdateNotificationSettingsRequest r) {
        NotificationSettings saved = repo.save(r.channels(), r.matrix());
        audit.record("NOTIFICATION_SETTINGS_UPDATE", "notification_channel", null,
                saved.channels().size() + " channel(s), " + saved.matrix().size() + " matrix cell(s)");
        return saved;
    }
}
