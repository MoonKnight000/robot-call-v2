package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.BillingModelPriceEntity;

public interface BillingModelPriceJpaRepository extends JpaRepository<BillingModelPriceEntity, String> {
}
