package uz.murodjon.robotcallv2.billing.application.dto;

import uz.murodjon.robotcallv2.billing.domain.enums.InvoiceStatus;

import java.time.Instant;

public record InvoiceDto(
        String id,
        String period,
        long amountUzs,
        InvoiceStatus status,
        Instant paidAt,
        String pdfUrl
) {
}
