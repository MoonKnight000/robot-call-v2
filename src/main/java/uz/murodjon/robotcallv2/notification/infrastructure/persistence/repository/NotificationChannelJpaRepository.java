package uz.murodjon.robotcallv2.notification.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.notification.infrastructure.persistence.entity.NotificationChannelEntity;

import java.util.List;

@Repository
public interface NotificationChannelJpaRepository extends JpaRepository<NotificationChannelEntity, Long> {

    List<NotificationChannelEntity> findByCompanyId(long companyId);

    void deleteByCompanyId(long companyId);
}
