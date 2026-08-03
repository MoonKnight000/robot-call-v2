package uz.murodjon.uysotvoice.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Global (not per-company) notification-channel settings (§11 settings) — one Telegram
 * bot serves every company; a company only supplies the destination {@code chat_id} as
 * its {@code notification_channel.target}. Blank token disables the Telegram channel
 * deployment-wide, same fail-closed posture as every other external credential here.
 */
@ConfigurationProperties(prefix = "voice-agent.notification")
public record NotificationProperties(
        String telegramBotToken
) {
}
