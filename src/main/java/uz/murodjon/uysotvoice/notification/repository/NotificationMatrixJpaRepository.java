package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.notification.entity.NotificationMatrixEntity;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;

import java.util.List;

/** Spring Data repository for {@link NotificationMatrixEntity}. */
public interface NotificationMatrixJpaRepository extends JpaRepository<NotificationMatrixEntity, Long> {

    List<NotificationMatrixEntity> findByCompanyId(long companyId);

    /**
     * The dispatch hot path (§11) — every enabled channel for one company's event type,
     * unscoped by {@code CurrentCompany} since {@code NotificationDispatchService} is
     * called with an explicit {@code companyId} from the event producer, not the
     * request thread.
     */
    List<NotificationMatrixEntity> findByCompanyIdAndTypeAndEnabledTrue(long companyId, NotificationType type);

    void deleteByCompanyId(long companyId);
}
