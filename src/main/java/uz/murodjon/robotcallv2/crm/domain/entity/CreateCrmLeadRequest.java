package uz.murodjon.robotcallv2.crm.domain.entity;

/**
 * A lead to open for a person who reached the company without being one yet.
 *
 * @param pipeStatusId  the stage it starts in — the company's {@code crm_settings.new_lead_status_id}
 * @param responsibleId the employee it is assigned to, or {@code null}
 * @param contactType   where the person came from, as the CRM names it ({@code TELEGRAM}, {@code WEBSITE}, {@code SIMPLE})
 */
public record CreateCrmLeadRequest(String name, String phone, long pipeStatusId, Long responsibleId,
                                   String contactType) {
}
