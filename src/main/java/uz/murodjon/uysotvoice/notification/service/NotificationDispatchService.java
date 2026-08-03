package uz.murodjon.uysotvoice.notification.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.notification.dto.NotificationChannel;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.notification.repository.NotificationSettingsRepository;

/**
 * Fans a company event out to its configured external channels (§11 settings) — layered
 * on top of the existing in-app bell ({@code NotificationService#notify}), called right
 * after it. Never throws: each sender already swallows its own failures, and a channel
 * with nothing configured is silently skipped.
 */
@Service
public class NotificationDispatchService {

    private final NotificationSettingsRepository repo;
    private final NotificationEmailSender email;
    private final NotificationWebhookSender webhook;
    private final NotificationTelegramSender telegram;

    public NotificationDispatchService(NotificationSettingsRepository repo, NotificationEmailSender email,
                                       NotificationWebhookSender webhook, NotificationTelegramSender telegram) {
        this.repo = repo;
        this.email = email;
        this.webhook = webhook;
        this.telegram = telegram;
    }

    public void dispatch(long companyId, NotificationType type, String title, String message, String link) {
        for (NotificationChannel channel : repo.enabledChannels(companyId, type)) {
            switch (channel.channel()) {
                case EMAIL -> email.send(channel.target(), title, message == null ? "" : message);
                case WEBHOOK -> webhook.send(channel.target(), type, title, message, link);
                case TELEGRAM -> telegram.send(channel.target(), title + (message != null ? "\n" + message : ""));
            }
        }
    }
}
