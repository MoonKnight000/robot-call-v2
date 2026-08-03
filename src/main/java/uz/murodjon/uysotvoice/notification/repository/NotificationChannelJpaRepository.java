package uz.murodjon.uysotvoice.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import uz.murodjon.uysotvoice.notification.entity.NotificationChannelEntity;

import java.util.List;

/** Spring Data repository for {@link NotificationChannelEntity}. */
public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannelEntity, Long> {

    List<NotificationChannelEntity> findByCompanyId(long companyId);

    void deleteByCompanyId(long companyId);
}
