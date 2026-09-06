package uz.murodjon.robotcallv2.notification.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
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

    public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository,
                                       AuditService auditService) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.auditService = auditService;
    }

    @Override
    public NotificationSettings findByCompanyId(long companyId) {
        return notificationSettingsRepository.findByCompanyId(companyId);
    }

    @Override
    public NotificationSettings updateByCompanyId(long companyId, UpdateNotificationSettingsRequest request) {
        NotificationSettings saved = notificationSettingsRepository.save(companyId, request.channels(), request.matrix());
        auditService.record(companyId, "NOTIFICATION_SETTINGS_UPDATE", "notification_channel", null,
                saved.channels().size() + " channel(s), " + saved.matrix().size() + " matrix cell(s)");
        return saved;
    }
}
