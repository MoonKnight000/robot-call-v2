package uz.murodjon.robotcallv2.notification.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.notification.application.mapper.NotificationSettingsMapper;
import uz.murodjon.robotcallv2.notification.application.port.output.NotificationSettingsRepository;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationChannel;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationMatrixEntry;
import uz.murodjon.robotcallv2.notification.domain.entity.NotificationSettings;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationChannelEntity;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository.NotificationChannelJpaRepository;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository.NotificationMatrixJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class NotificationSettingsRepositoryAdapter implements NotificationSettingsRepository {

    private final NotificationChannelJpaRepository notificationChannelJpaRepository;
    private final NotificationMatrixJpaRepository notificationMatrixJpaRepository;
    private final NotificationSettingsMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public NotificationSettingsRepositoryAdapter(NotificationChannelJpaRepository notificationChannelJpaRepository,
                                                 NotificationMatrixJpaRepository notificationMatrixJpaRepository,
                                                 NotificationSettingsMapper mapper,
                                                 CompanyJpaRepository companyJpaRepository) {
        this.notificationChannelJpaRepository = notificationChannelJpaRepository;
        this.notificationMatrixJpaRepository = notificationMatrixJpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public NotificationSettings findByCompanyId(long companyId) {
        List<NotificationChannel> channelRows = notificationChannelJpaRepository.findByCompanyId(companyId).stream()
                .map(mapper::channelEntityToDomain)
                .toList();
        List<NotificationMatrixEntry> matrixRows = notificationMatrixJpaRepository.findByCompanyId(companyId).stream()
                .map(mapper::matrixEntityToDomain)
                .toList();
        return new NotificationSettings(channelRows, matrixRows);
    }

    @Override
    @Transactional
    public NotificationSettings save(long companyId, List<NotificationChannel> channelRows, List<NotificationMatrixEntry> matrixRows) {
        notificationChannelJpaRepository.deleteByCompanyId(companyId);
        notificationMatrixJpaRepository.deleteByCompanyId(companyId);
        Instant now = Instant.now();
        CompanyEntity company = companyJpaRepository.getReferenceById(companyId);
        for (NotificationChannel row : channelRows) {
            notificationChannelJpaRepository.save(mapper.channelDomainToEntity(row, company, now));
        }
        for (NotificationMatrixEntry row : matrixRows) {
            notificationMatrixJpaRepository.save(mapper.matrixDomainToEntity(row, company));
        }
        return findByCompanyId(companyId);
    }

    @Override
    public List<NotificationChannel> enabledChannels(long companyId, NotificationType type) {
        List<NotificationChannelEntity> configured = notificationChannelJpaRepository.findByCompanyId(companyId);
        return notificationMatrixJpaRepository.findByCompanyIdAndTypeAndEnabledTrue(companyId, type).stream()
                .flatMap(m -> configured.stream()
                        .filter(c -> c.getChannel() == m.getChannel() && c.isEnabled()
                                && c.getTarget() != null && !c.getTarget().isBlank()))
                .map(c -> new NotificationChannel(c.getChannel(), c.getTarget(), true))
                .toList();
    }
}
