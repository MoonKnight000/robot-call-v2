package uz.murodjon.robotcallv2.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global (not per-company) notification-channel settings (§11 settings).
 *
 * @param telegramBotToken Telegram bot that sends the TELEGRAM channel
 * @param emailFrom        sender address of the EMAIL channel
 */
@ConfigurationProperties(prefix = "voice-agent.notification")
public record NotificationProperties(
        String telegramBotToken,
        String emailFrom
) {

    public NotificationProperties {
        if (emailFrom == null || emailFrom.isBlank()) {
            emailFrom = "no-reply@uysot.uz";
        }
    }
}
