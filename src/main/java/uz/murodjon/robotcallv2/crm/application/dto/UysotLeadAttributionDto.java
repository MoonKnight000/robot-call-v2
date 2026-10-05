package uz.murodjon.robotcallv2.crm.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Attribution source for a lead (e.g. INSTAGRAM, TELEGRAM, WEBSITE).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UysotLeadAttributionDto(
        Long channelId,
        String source,
        String channelValue,
        Long attachedTimestamp
) {
}
