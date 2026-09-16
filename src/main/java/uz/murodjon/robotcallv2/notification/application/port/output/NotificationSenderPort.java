package uz.murodjon.robotcallv2.notification.application.port.output;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

/** Delivers one company event over one external channel (§11 settings). Best effort: never throws. */
public interface NotificationSenderPort {

    NotificationChannelType resolveChannelType();

    /**
     * @param signingSecret the company's {@code WEBHOOK_SIGNING_SECRET}, or null when it has
     *                      none; a channel that can carry a signature (the webhook) signs
     *                      with it, the others ignore it
     */
    void send(String target, NotificationType type, String title, String message, String link, String signingSecret);
}
