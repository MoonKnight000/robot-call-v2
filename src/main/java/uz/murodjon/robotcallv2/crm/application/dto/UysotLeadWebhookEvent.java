package uz.murodjon.robotcallv2.crm.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Inbound webhook payload from Uysot Open API (e.g. LEAD_CREATED).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UysotLeadWebhookEvent(
        String eventId,
        @NotBlank String eventType,
        Long occurredAt,
        @NotNull Long companyId,
        UysotLeadPayloadDto lead
) {
}
