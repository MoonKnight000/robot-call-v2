package uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationMatrixEntity;

import java.util.List;

@Repository
public interface NotificationMatrixJpaRepository extends JpaRepository<NotificationMatrixEntity, Long> {

    List<NotificationMatrixEntity> findByCompanyId(long companyId);

    List<NotificationMatrixEntity> findByCompanyIdAndTypeAndEnabledTrue(long companyId, NotificationType type);

    void deleteByCompanyId(long companyId);
}
