package uz.murodjon.robotcallv2.notification.application.port.input;

import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;

public interface NotificationSettingsUseCase {

    NotificationSettings find();

    NotificationSettings update(UpdateNotificationSettingsRequest r);
}
