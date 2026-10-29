package uz.murodjon.robotcallv2.crm.application.dto;

import java.time.Instant;

/**
 * Instruction to schedule a task or meeting in the company's CRM.
 */
public record CreateCrmTaskRequest(
        long leadId,
        String title,
        String description,
        Instant dueAt,
        Long responsibleId,
        Long taskTypeId
) {

    /** A task of the CRM's default type — the company's {@code crm_settings.task_type_id}, filled in by CrmService. */
    public CreateCrmTaskRequest(long leadId, String title, String description, Instant dueAt, Long responsibleId) {
        this(leadId, title, description, dueAt, responsibleId, null);
    }
}
