package uz.murodjon.robotcallv2.billing.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.billing.application.mapper.InvoiceMapper;
import uz.murodjon.robotcallv2.billing.application.port.output.InvoiceRepository;
import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.InvoiceEntity;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.repository.InvoiceJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;

import java.util.List;
import java.util.Optional;

@Component
public class InvoiceRepositoryAdapter implements InvoiceRepository {

    private final InvoiceJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final InvoiceMapper mapper;

    public InvoiceRepositoryAdapter(InvoiceJpaRepository jpaRepository,
                                    CompanyJpaRepository companyJpaRepository,
                                    InvoiceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Invoice> findById(String id) {
        return jpaRepository.findById(id).map(mapper::toInvoice);
    }

    @Override
    public PageableData<Invoice> findAllByCompanyId(long companyId, int page, int size) {
        Page<InvoiceEntity> p = jpaRepository.findAllByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(page, size));
        List<Invoice> list = p.getContent().stream().map(mapper::toInvoice).toList();
        return new PageableData<>(p.getTotalPages(), p.getNumber(), p.getTotalElements(), list);
    }

    @Override
    public Invoice save(Invoice invoice) {
        CompanyEntity company = companyJpaRepository.getReferenceById(invoice.companyId());
        InvoiceEntity entity = mapper.toEntity(invoice, company);
        return mapper.toInvoice(jpaRepository.save(entity));
    }
}
