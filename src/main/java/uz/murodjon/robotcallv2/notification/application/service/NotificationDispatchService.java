package uz.murodjon.robotcallv2.notification.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.request.adapter.NotificationEmailSender;
import uz.murodjon.robotcallv2.notification.infrastructure.request.adapter.NotificationTelegramSender;
import uz.murodjon.robotcallv2.notification.infrastructure.request.adapter.NotificationWebhookSender;

/**
 * Fans a company event out to its configured external channels (§11 settings).
 */
@Service
public class NotificationDispatchService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final NotificationEmailSender notificationEmailSender;
    private final NotificationWebhookSender notificationWebhookSender;
    private final NotificationTelegramSender notificationTelegramSender;

    public NotificationDispatchService(NotificationSettingsRepository notificationSettingsRepository,
                                       NotificationEmailSender notificationEmailSender,
                                       NotificationWebhookSender notificationWebhookSender,
                                       NotificationTelegramSender notificationTelegramSender) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.notificationEmailSender = notificationEmailSender;
        this.notificationWebhookSender = notificationWebhookSender;
        this.notificationTelegramSender = notificationTelegramSender;
    }

    public void dispatch(long companyId, NotificationType type, String title, String message, String link) {
        for (NotificationChannel channel : notificationSettingsRepository.enabledChannels(companyId, type)) {
            switch (channel.channel()) {
                case EMAIL -> notificationEmailSender.send(channel.target(), title, message == null ? "" : message);
                case WEBHOOK -> notificationWebhookSender.send(channel.target(), type, title, message, link);
                case TELEGRAM -> notificationTelegramSender.send(channel.target(), title + (message != null ? "\n" + message : ""));
            }
        }
    }
}
