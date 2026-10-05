package uz.murodjon.robotcallv2.crm.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Lead details carried by a Uysot CRM webhook event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UysotLeadPayloadDto(
        Long id,
        String name,
        Long statusId,
        String statusName,
        Long pipeId,
        String pipeName,
        Long responsibleId,
        String responsibleName,
        Double balance,
        String currency,
        String phone,
        List<UysotLeadAttributionDto> attributions,
        String uuid,
        Long createdTimestamp,
        Long updatedTimestamp
) {
    public UysotLeadPayloadDto(Long id, String name, Long statusId, String statusName,
                               Long pipeId, String pipeName, Long responsibleId, String responsibleName,
                               Double balance, String currency, String phone,
                               List<UysotLeadAttributionDto> attributions) {
        this(id, name, statusId, statusName, pipeId, pipeName, responsibleId, responsibleName,
                balance, currency, phone, attributions, null, null, null);
    }
}
