package uz.murodjon.robotcallv2.crm.domain.entity;

import java.math.BigDecimal;

/**
 * One client the CRM says is behind on a contract, with everything needed to call them.
 *
 * <p>Assembled from three Uysot reads — the overdue contract, its lead, and that lead's
 * contacts — because no single endpoint answers with both the debt and a phone number.
 *
 * <p>There is deliberately no due date. The debt-collection scenario's {@code factSchema}
 * has an optional {@code dueDate}, and Uysot's contract row carries {@code delay} in days
 * rather than the date it counts from: today minus {@code delay} would be a date this
 * system computed and the agent would then state as fact, which is the one thing §4.4
 * forbids. It is left unset and the agent says nothing about a date.
 *
 * @param leadId      Uysot lead id — stored as the target's {@code client_id}, and what
 *                    the post-call note is later attached to
 * @param debtAmount  the contract's remaining amount, stated verbatim by the agent
 * @param delayDays   days the contract is behind, for logging and for ordering the list
 */
public record Debtor(
        long leadId,
        String phone,
        String name,
        String contractNumber,
        BigDecimal debtAmount,
        String currency,
        int delayDays
) {
}
