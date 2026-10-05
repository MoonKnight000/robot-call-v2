package uz.murodjon.robotcallv2.crm.application.dto;

/**
 * Request to update a lead's status/stage in the CRM.
 */
public record UpdateLeadStageRequest(
        long leadId,
        long statusId,
        String reason
) {
}
