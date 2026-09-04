package uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.InvoiceEntity;

public interface InvoiceJpaRepository extends JpaRepository<InvoiceEntity, String> {

    Page<InvoiceEntity> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);
}
