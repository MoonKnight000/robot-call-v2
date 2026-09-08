package uz.murodjon.robotcallv2.conversion.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

/**
 * What a company's own system posts when something it counts as a result happened.
 *
 * @param goalKey    which goal this is, as configured on the company
 * @param phone      the customer's number; matched against the numbers we dialled
 * @param occurredAt when it happened — not when it was posted, which may be much later
 * @param valueUzs   what it was worth, or null for a goal with no money attached
 * @param source     where it came from: {@code crm}, {@code payme}, an integration's name
 * @param dedupeKey  the poster's own id for the thing; a retry with the same key changes nothing
 * @param evidence   anything worth keeping for a later argument: an order id, a receipt
 */
public record ReportConversionRequest(
        @NotBlank(message = "goalKey kiritilishi shart")
        @Size(max = 64, message = "goalKey 64 belgidan oshmasin")
        String goalKey,

        @NotBlank(message = "phone kiritilishi shart")
        String phone,

        @NotNull(message = "occurredAt kiritilishi shart")
        Instant occurredAt,

        Long valueUzs,

        @Size(max = 64, message = "source 64 belgidan oshmasin")
        String source,

        @NotBlank(message = "dedupeKey kiritilishi shart")
        @Size(max = 128, message = "dedupeKey 128 belgidan oshmasin")
        String dedupeKey,

        Map<String, Object> evidence
) {
}
