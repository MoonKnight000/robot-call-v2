package uz.murodjon.robotcallv2.notification.domain.service;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationChannelType;

public final class NotificationValidator {

    private NotificationValidator() {
    }

    public static boolean isValidTarget(NotificationChannelType type, String target) {
        if (target == null || target.isBlank()) {
            return false;
        }
        return switch (type) {
            case EMAIL -> target.contains("@");
            case WEBHOOK -> target.startsWith("http://") || target.startsWith("https://");
            case TELEGRAM -> !target.isBlank();
        };
    }
}
