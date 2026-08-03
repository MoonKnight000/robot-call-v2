package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.notification.dto.NotificationChannel;
import uz.murodjon.uysotvoice.notification.dto.NotificationMatrixEntry;
import uz.murodjon.uysotvoice.notification.dto.NotificationSettings;
import uz.murodjon.uysotvoice.notification.entity.NotificationChannelEntity;
import uz.murodjon.uysotvoice.notification.entity.NotificationMatrixEntity;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;

import java.time.Instant;
import java.util.List;

/** JPA-backed DAO for {@code notification_channel}/{@code notification_matrix} (§11 settings). */
@Repository
public class NotificationSettingsRepository {

    private final NotificationChannelJpaRepository channels;
    private final NotificationMatrixJpaRepository matrix;
    private final CurrentCompany company;

    public NotificationSettingsRepository(NotificationChannelJpaRepository channels,
                                          NotificationMatrixJpaRepository matrix, CurrentCompany company) {
        this.channels = channels;
        this.matrix = matrix;
        this.company = company;
    }

    public NotificationSettings find() {
        long companyId = company.id();
        List<NotificationChannel> channelRows = channels.findByCompanyId(companyId).stream()
                .map(e -> new NotificationChannel(e.getChannel(), e.getTarget(), e.isEnabled()))
                .toList();
        List<NotificationMatrixEntry> matrixRows = matrix.findByCompanyId(companyId).stream()
                .map(e -> new NotificationMatrixEntry(e.getType(), e.getChannel(), e.isEnabled()))
                .toList();
        return new NotificationSettings(channelRows, matrixRows);
    }

    /** Whole-grid replace: simpler and safer than diffing against what a client might not have fetched fresh. */
    @Transactional
    public NotificationSettings save(List<NotificationChannel> channelRows, List<NotificationMatrixEntry> matrixRows) {
        long companyId = company.id();
        channels.deleteByCompanyId(companyId);
        matrix.deleteByCompanyId(companyId);
        Instant now = Instant.now();
        for (NotificationChannel row : channelRows) {
            NotificationChannelEntity entity = new NotificationChannelEntity();
            entity.setCompanyId(companyId);
            entity.setChannel(row.channel());
            entity.setTarget(row.target());
            entity.setEnabled(row.enabled());
            entity.setCreatedAt(now);
            channels.save(entity);
        }
        for (NotificationMatrixEntry row : matrixRows) {
            NotificationMatrixEntity entity = new NotificationMatrixEntity();
            entity.setCompanyId(companyId);
            entity.setType(row.type());
            entity.setChannel(row.channel());
            entity.setEnabled(row.enabled());
            matrix.save(entity);
        }
        return find();
    }

    /**
     * The dispatch hot path (§11) — {@code companyId} explicit rather than {@code
     * CurrentCompany}, since {@code NotificationDispatchService} is called from an event
     * producer (e.g. {@code AlertingService}), not the settings-page request thread.
     * Only channels that are both matrix-enabled for {@code type} and themselves
     * enabled+configured are returned — a channel switched off, or with no target set,
     * sends nothing even if the matrix cell says yes.
     */
    public List<NotificationChannel> enabledChannels(long companyId, NotificationType type) {
        List<NotificationChannelEntity> configured = channels.findByCompanyId(companyId);
        return matrix.findByCompanyIdAndTypeAndEnabledTrue(companyId, type).stream()
                .flatMap(m -> configured.stream()
                        .filter(c -> c.getChannel() == m.getChannel() && c.isEnabled()
                                && c.getTarget() != null && !c.getTarget().isBlank()))
                .map(c -> new NotificationChannel(c.getChannel(), c.getTarget(), true))
                .toList();
    }
}
