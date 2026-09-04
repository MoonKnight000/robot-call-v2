package uz.murodjon.robotcallv2.billing.domain.entity;

import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;

import java.time.Instant;

public record Invoice(
        String id,
        long companyId,
        String periodName,
        long amountUzs,
        InvoiceStatus status,
        Instant paidAt,
        String pdfFilePath,
        Instant createdAt
) {
}
