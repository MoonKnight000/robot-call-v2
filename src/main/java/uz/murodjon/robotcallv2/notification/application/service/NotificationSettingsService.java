package uz.murodjon.robotcallv2.notification.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationSettingsUseCase;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;

/**
 * Company-level notification channel x event matrix CRUD (§11 settings).
 */
@Service
public class NotificationSettingsService implements NotificationSettingsUseCase {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final AuditService auditService;
    private final CurrentCompany currentCompany;

    public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository,
                                       AuditService auditService,
                                       CurrentCompany currentCompany) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.auditService = auditService;
        this.currentCompany = currentCompany;
    }

    @Override
    public NotificationSettings find() {
        return notificationSettingsRepository.findByCompanyId(currentCompany.id());
    }

    @Override
    public NotificationSettings update(UpdateNotificationSettingsRequest r) {
        long companyId = currentCompany.id();
        NotificationSettings saved = notificationSettingsRepository.save(companyId, r.channels(), r.matrix());
        auditService.record("NOTIFICATION_SETTINGS_UPDATE", "notification_channel", null,
                saved.channels().size() + " channel(s), " + saved.matrix().size() + " matrix cell(s)");
        return saved;
    }
}
