package uz.murodjon.robotcallv2.promise.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.promise.infrastructure.persistence.entity.PaymentPromiseSettingsEntity;

public interface PaymentPromiseSettingsJpaRepository extends JpaRepository<PaymentPromiseSettingsEntity, Long> {
}
