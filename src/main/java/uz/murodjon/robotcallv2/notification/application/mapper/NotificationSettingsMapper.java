package uz.murodjon.robotcallv2.notification.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationMatrixEntry;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationChannelEntity;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationMatrixEntity;

import java.time.Instant;

@Component
public class NotificationSettingsMapper {

    public NotificationChannel channelEntityToDomain(NotificationChannelEntity entity) {
        if (entity == null) {
            return null;
        }
        return new NotificationChannel(entity.getChannel(), entity.getTarget(), entity.isEnabled());
    }

    public NotificationChannelEntity channelDomainToEntity(NotificationChannel domain, long companyId, Instant now) {
        if (domain == null) {
            return null;
        }
        NotificationChannelEntity entity = new NotificationChannelEntity();
        entity.setCompanyId(companyId);
        entity.setChannel(domain.channel());
        entity.setTarget(domain.target());
        entity.setEnabled(domain.enabled());
        entity.setCreatedAt(now);
        return entity;
    }

    public NotificationMatrixEntry matrixEntityToDomain(NotificationMatrixEntity entity) {
        if (entity == null) {
            return null;
        }
        return new NotificationMatrixEntry(entity.getType(), entity.getChannel(), entity.isEnabled());
    }

    public NotificationMatrixEntity matrixDomainToEntity(NotificationMatrixEntry domain, long companyId) {
        if (domain == null) {
            return null;
        }
        NotificationMatrixEntity entity = new NotificationMatrixEntity();
        entity.setCompanyId(companyId);
        entity.setType(domain.type());
        entity.setChannel(domain.channel());
        entity.setEnabled(domain.enabled());
        return entity;
    }
}
