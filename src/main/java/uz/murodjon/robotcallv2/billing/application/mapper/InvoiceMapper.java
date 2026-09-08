package uz.murodjon.robotcallv2.billing.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.billing.domain.entity.Invoice;
import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;
import uz.murodjon.robotcallv2.billing.infrastructure.persistence.entity.InvoiceEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;

@Component
public class InvoiceMapper {

    public Invoice toInvoice(InvoiceEntity entity) {
        if (entity == null) {
            return null;
        }
        return new Invoice(
                entity.getId(),
                entity.getCompanyId(),
                entity.getPeriodName(),
                entity.getAmountUzs() != null ? entity.getAmountUzs() : 0L,
                entity.getStatus() != null ? entity.getStatus() : InvoiceStatus.PENDING,
                entity.getPaidAt(),
                entity.getPdfFilePath(),
                entity.getCreatedAt());
    }

    public InvoiceEntity toEntity(Invoice invoice, CompanyEntity company) {
        if (invoice == null) {
            return null;
        }
        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(invoice.id());
        entity.setCompany(company);
        entity.setPeriodName(invoice.periodName());
        entity.setAmountUzs(invoice.amountUzs());
        entity.setStatus(invoice.status());
        entity.setPaidAt(invoice.paidAt());
        entity.setPdfFilePath(invoice.pdfFilePath());
        entity.setCreatedAt(invoice.createdAt());
        return entity;
    }
}
