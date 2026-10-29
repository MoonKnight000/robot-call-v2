package uz.murodjon.robotcallv2.crm.domain.entity;

import java.math.BigDecimal;

/**
 * One sale contract, with the figures the agent may state as they are.
 *
 * @param residue   what is left to pay
 * @param delayDays how many days the contract is behind schedule (0 = on time)
 * @param leadId    the CRM lead the contract belongs to, or {@code null}
 * @param responsibleEmployeeId the manager responsible for the contract, or {@code null}
 */
public record CrmContract(
        long id,
        String number,
        String clientName,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal residue,
        String currency,
        int delayDays,
        String status,
        Long leadId,
        Long responsibleEmployeeId,
        String responsibleEmployeeName
) {
}
