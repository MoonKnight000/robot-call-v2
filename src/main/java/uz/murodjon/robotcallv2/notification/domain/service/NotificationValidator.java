package uz.murodjon.robotcallv2.notification.domain.service;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

public final class NotificationValidator {

    private NotificationValidator() {
    }

    public static boolean isValidTarget(NotificationChannelType type, String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        return switch (type) {
            case EMAIL -> target.contains("@");
            // Not only the scheme: this server POSTs to it, so an internal address
            // (asterisk:8088, minio:9000, 169.254.169.254) would be a request forger.
            case WEBHOOK -> PublicUrlGuard.parsePublic(target) != null;
            case TELEGRAM -> !target.isBlank();
        };
    }
}
