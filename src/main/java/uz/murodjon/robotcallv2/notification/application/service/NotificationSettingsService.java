package uz.murodjon.robotcallv2.notification.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationSettingsUseCase;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;
import uz.murodjon.robotcallv2.notification.domain.service.NotificationValidator;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * Company-level notification channel x event matrix CRUD (§11 settings).
 */
@Service
public class NotificationSettingsService implements NotificationSettingsUseCase {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final AuditUseCase auditService;

    public NotificationSettingsService(NotificationSettingsRepository notificationSettingsRepository,
                                       AuditUseCase auditService) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.auditService = auditService;
    }

    @Override
    public NotificationSettings findByCompanyId(long companyId) {
        return notificationSettingsRepository.findByCompanyId(companyId);
    }

    @Override
    public NotificationSettings updateByCompanyId(long companyId, UpdateNotificationSettingsRequest request) {
        if (request.channels() != null) {
            for (NotificationChannel channel : request.channels()) {
                boolean hasTarget = channel.target() != null && !channel.target().isBlank();
                if ((channel.enabled() || hasTarget) && !NotificationValidator.isValidTarget(channel.channel(), channel.target())) {
                    throw new ValidationException(ErrorCode.NOTIFICATION_CHANNEL_TARGET_INVALID, channel.channel());
                }
            }
        }
        NotificationSettings saved = notificationSettingsRepository.save(companyId, request.channels(), request.matrix());
        auditService.record(companyId, "NOTIFICATION_SETTINGS_UPDATE", "notification_channel", null,
                saved.channels().size() + " channel(s), " + saved.matrix().size() + " matrix cell(s)");
        return saved;
    }
}
