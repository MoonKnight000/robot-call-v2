package uz.murodjon.robotcallv2.notification.application.port.input;

import uz.murodjon.robotcallv2.notification.application.dto.UpdateNotificationSettingsRequest;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;

public interface NotificationSettingsUseCase {

    NotificationSettings findByCompanyId(long companyId);

    NotificationSettings updateByCompanyId(long companyId, UpdateNotificationSettingsRequest request);
}
