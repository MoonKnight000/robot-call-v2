package uz.murodjon.robotcallv2.callrecord.domain.entity;

import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageKind;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageStatus;

import java.time.Instant;
import java.util.Map;

/**
 * One write a finished call owes another system, kept until that system accepts it
 * (docs/AUDIT-2026-09-13.md §10.10).
 *
 * @param callAttemptId  the call the write is about
 * @param payload        what the delivery needs, by name; each kind's keys are listed in
 *                       {@code OutboxDeliveryService}
 * @param idempotencyKey kind + call attempt — a call gets at most one message of each kind
 * @param attempts       delivery attempts started so far; counted when a worker claims the message
 * @param nextAttemptAt  when the message is due next; while a worker holds it, the end of that
 *                       worker's lease
 * @param lastError      why the latest attempt failed; null once the message is sent
 */
public record OutboxMessage(
        Long id,
        long companyId,
        OutboxMessageKind kind,
        long callAttemptId,
        Map<String, Object> payload,
        String idempotencyKey,
        OutboxMessageStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant sentAt
) {

    /**
     * A message to enqueue. Id, company, due time and creation time are left for the adapter
     * to stamp.
     */
    public static OutboxMessage pending(OutboxMessageKind kind, long callAttemptId, Map<String, Object> payload) {
        return new OutboxMessage(null, 0L, kind, callAttemptId, payload, kind.name() + ":" + callAttemptId,
                OutboxMessageStatus.PENDING, 0, null, null, null, null);
    }

    /**
     * The remote system took it. The payload is dropped: a sent row keeps no copy of what it
     * carried — a zero-PII agent's note text, a caller's number.
     */
    public OutboxMessage markSent(Instant at) {
        return new OutboxMessage(id, companyId, kind, callAttemptId, Map.of(), idempotencyKey,
                OutboxMessageStatus.SENT, attempts, nextAttemptAt, null, createdAt, at);
    }

    /** The attempt failed; due again at {@code at}. */
    public OutboxMessage scheduleRetry(Instant at, String error) {
        return new OutboxMessage(id, companyId, kind, callAttemptId, payload, idempotencyKey,
                OutboxMessageStatus.PENDING, attempts, at, error, createdAt, null);
    }

    /** The last allowed attempt failed; nothing tries it again. */
    public OutboxMessage markDead(String error) {
        return new OutboxMessage(id, companyId, kind, callAttemptId, payload, idempotencyKey,
                OutboxMessageStatus.DEAD, attempts, nextAttemptAt, error, createdAt, null);
    }
}
