package uz.murodjon.robotcallv2.notification.application.port.output;

import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationMatrixEntry;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.util.List;

public interface NotificationSettingsRepository {

    NotificationSettings findByCompanyId(long companyId);

    NotificationSettings save(long companyId, List<NotificationChannel> channelRows, List<NotificationMatrixEntry> matrixRows);

    List<NotificationChannel> enabledChannels(long companyId, NotificationType type);
}
