package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.port.output.InvoiceRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.InvoiceEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.InvoiceJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;
import java.util.Optional;

@Component
public class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final InvoiceJpaRepository jpa;

    public InvoiceRepositoryAdapter(InvoiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Invoice> findById(String id) {
        return jpa.findById(id).map(InvoiceEntity::toDomain);
    }

    @Override
    public PageableData<Invoice> findAllByCompanyId(long companyId, int page, int size) {
        Page<InvoiceEntity> p = jpa.findAllByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(page, size));
        List<Invoice> list = p.getContent().stream().map(InvoiceEntity::toDomain).toList();
        return new PageableData<>(p.getTotalPages(), p.getNumber(), p.getTotalElements(), list);
    }

    @Override
    public Invoice save(Invoice invoice) {
        InvoiceEntity entity = InvoiceEntity.fromDomain(invoice);
        return jpa.save(entity).toDomain();
    }
}
