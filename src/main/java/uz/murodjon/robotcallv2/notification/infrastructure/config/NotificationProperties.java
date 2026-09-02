package uz.murodjon.robotcallv2.notification.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global (not per-company) notification-channel settings (§11 settings).
 */
@ConfigurationProperties(prefix = "voice-agent.notification")
public record NotificationProperties(
        String telegramBotToken
) {
}
