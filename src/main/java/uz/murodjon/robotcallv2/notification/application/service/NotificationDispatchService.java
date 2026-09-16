package uz.murodjon.robotcallv2.notification.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSenderPort;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.webhook.domain.service.WebhookSigningSecret;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Fans a company event out to its configured external channels (§11 settings).
 *
 * <p>A webhook channel is signed with the company's {@code WEBHOOK_SIGNING_SECRET} — the
 * same secret and format as its post-call webhooks, so a receiver verifies both alike.
 * The secret is read only when a webhook channel is about to be used.
 */
@Service
public class NotificationDispatchService {

    private final NotificationSettingsRepository notificationSettingsRepository;
    private final SecretUseCase secretUseCase;
    private final Map<NotificationChannelType, NotificationSenderPort> senders = new EnumMap<>(NotificationChannelType.class);

    public NotificationDispatchService(NotificationSettingsRepository notificationSettingsRepository,
                                       SecretUseCase secretUseCase,
                                       List<NotificationSenderPort> senders) {
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.secretUseCase = secretUseCase;
        senders.forEach(sender -> this.senders.put(sender.resolveChannelType(), sender));
    }

    public void dispatch(long companyId, NotificationType type, String title, String message, String link) {
        List<NotificationChannel> channels = notificationSettingsRepository.findEnabledChannelsByCompanyIdAndType(companyId, type);
        String signingSecret = channels.stream().anyMatch(channel -> channel.channel() == NotificationChannelType.WEBHOOK)
                ? WebhookSigningSecret.findIn(secretUseCase.findDecryptedSecrets(companyId))
                : null;
        for (NotificationChannel channel : channels) {
            NotificationSenderPort sender = senders.get(channel.channel());
            if (sender != null) {
                sender.send(channel.target(), type, title, message, link, signingSecret);
            }
        }
    }
}
