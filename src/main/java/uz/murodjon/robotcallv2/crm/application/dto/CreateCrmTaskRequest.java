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
        Long responsibleId
) {
}
