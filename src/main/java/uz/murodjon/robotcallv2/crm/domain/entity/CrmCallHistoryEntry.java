package uz.murodjon.robotcallv2.crm.domain.entity;

import java.time.Instant;

/**
 * One conversation, as a CRM's call log records it.
 *
 * @param leadId          the CRM record the call is about
 * @param callUuid        this platform's own id for the conversation — the idempotency key,
 *                        so a re-post after a failed write does not create a second record
 * @param durationSeconds talk time
 * @param clientPhone     the other party's number, or {@code null}
 * @param recordingUrl    a link the CRM can play the recording from, or {@code null}
 */
public record CrmCallHistoryEntry(
        long leadId,
        String callUuid,
        Instant startedAt,
        int durationSeconds,
        boolean inbound,
        boolean answered,
        String clientPhone,
        String recordingUrl
) {
}
