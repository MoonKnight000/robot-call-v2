package uz.murodjon.robotcallv2.callrecord.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.dialog.CallSummary;
import uz.murodjon.robotcallv2.callrecord.application.port.input.OutboxMessageUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.OutboxMessageRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;
import uz.murodjon.robotcallv2.callrecord.domain.enums.OutboxMessageKind;
import uz.murodjon.robotcallv2.callrecord.infrastructure.config.OutboxProperties;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmCallHistoryEntry;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Delivers the writes a finished call owes the CRM — the note and the call-history entry
 * (docs/AUDIT-2026-09-13.md §4.12, §10.10).
 *
 * <p>Every write is an {@code outbox_message} row before it is a request. The note's row is
 * stored in the transaction that stores the call's result ({@link CallRecordService#writeResult});
 * the call history's is an insert of its own. Right after, the finalizer delivers the row once
 * ({@link #deliverNow}) — as soon as when these writes were made inline. Whatever did not land
 * is retried by {@link #deliverDue} with exponential backoff, and after {@code max-attempts}
 * the row is DEAD and an alert is raised.
 *
 * <p>Both paths take a row the same way: its attempt is counted and its {@code next_attempt_at}
 * pushed past a lease, in a transaction of its own, and only then is the CRM called — outside
 * any transaction. So one worker sends a row at a time, and a SENT row is never sent again.
 * What is left is at-least-once: when the CRM took a write but its answer never came back (a
 * timeout, or this instance dying before the row was marked), the write is sent again. The call
 * history carries this platform's call id, which the CRM de-duplicates on; Uysot's note endpoint
 * takes no such key, so there a lost answer leaves a second copy of the note on the lead.
 *
 * <p>Payload keys — {@code CRM_NOTE}: {@code leadId}, {@code noteText}. {@code CRM_CALL_HISTORY}:
 * {@code leadId}, {@code callUuid}, {@code startedAt} (ISO-8601), {@code durationSeconds},
 * {@code inbound}, {@code answered}, {@code clientPhone}.
 */
@Service
public class OutboxDeliveryService implements OutboxMessageUseCase {

    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryService.class);

    private static final String LEAD_ID = "leadId";
    private static final String NOTE_TEXT = "noteText";
    private static final String CALL_UUID = "callUuid";
    private static final String STARTED_AT = "startedAt";
    private static final String DURATION_SECONDS = "durationSeconds";
    private static final String INBOUND = "inbound";
    private static final String ANSWERED = "answered";
    private static final String CLIENT_PHONE = "clientPhone";

    /**
     * How long a claimed row stays hidden from other workers — far longer than a CRM request
     * is allowed to take, so it only runs out on a worker that died mid-delivery.
     */
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    /** Why a message still waiting to be delivered was given up: the person asked to be forgotten. */
    private static final String ERASED_REASON = "subject data erased";

    private final OutboxMessageRepository outboxMessageRepository;
    private final CallResultRepository callResultRepository;
    private final CrmUseCase crmUseCase;
    private final NotificationUseCase notificationUseCase;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    public OutboxDeliveryService(OutboxMessageRepository outboxMessageRepository,
                                 CallResultRepository callResultRepository,
                                 CrmUseCase crmUseCase,
                                 NotificationUseCase notificationUseCase,
                                 OutboxProperties outboxProperties,
                                 Clock clock) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.callResultRepository = callResultRepository;
        this.crmUseCase = crmUseCase;
        this.notificationUseCase = notificationUseCase;
        this.outboxProperties = outboxProperties;
        this.clock = clock;
    }

    /**
     * The CRM note for a summarized call, to be stored with the call's result — or
     * {@code null} when there is nobody to file it under: a call with no CRM lead (inbound,
     * manual, widget) or a company with no CRM. Those get no outbox row at all, rather than a
     * row that fails until it is dead.
     */
    public OutboxMessage prepareCrmNote(long companyId, long callAttemptId, long leadId, CallSummary summary) {
        if (leadId <= 0 || summary == null || !crmUseCase.isConnected(companyId)) {
            return null;
        }
        return OutboxMessage.pending(OutboxMessageKind.CRM_NOTE, callAttemptId,
                Map.of(LEAD_ID, leadId, NOTE_TEXT, crmUseCase.composeNoteText(summary)));
    }

    /**
     * Queues the call's entry on the lead's call history and delivers it once. Skipped, like
     * {@link #prepareCrmNote}, for a call with no CRM lead or a company with no CRM.
     */
    public void enqueueCallHistory(long companyId, long callAttemptId, CrmCallHistoryEntry entry) {
        if (entry.leadId() <= 0 || entry.startedAt() == null || !crmUseCase.isConnected(companyId)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>(); // the phone may be null, which Map.of refuses
        payload.put(LEAD_ID, entry.leadId());
        payload.put(CALL_UUID, entry.callUuid());
        payload.put(STARTED_AT, entry.startedAt().toString());
        payload.put(DURATION_SECONDS, entry.durationSeconds());
        payload.put(INBOUND, entry.inbound());
        payload.put(ANSWERED, entry.answered());
        payload.put(CLIENT_PHONE, entry.clientPhone());
        Long id = outboxMessageRepository.enqueue(companyId,
                OutboxMessage.pending(OutboxMessageKind.CRM_CALL_HISTORY, callAttemptId, payload));
        if (id != null) {
            deliverNow(id);
        }
    }

    /**
     * The first delivery of a row just enqueued, on the caller's thread. Does nothing when a
     * worker claimed the row first.
     */
    public void deliverNow(long messageId) {
        try {
            OutboxMessage claimed = outboxMessageRepository.claimFresh(messageId, clock.instant().plus(LEASE));
            if (claimed != null) {
                deliver(claimed);
            }
        } catch (Exception e) {
            log.warn("Outbox: first delivery of message {} failed, left to the retry: {}", messageId, e.getMessage());
        }
    }

    /**
     * What a data-subject erasure leaves of the messages about the number's calls: nothing
     * to deliver, and no copy of what they were to carry.
     */
    @Override
    public int eraseMessagesOfPhones(long companyId, Collection<String> phones) {
        return outboxMessageRepository.erasePayloadsByPhones(companyId, phones, ERASED_REASON);
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.outbox.delivery-interval-seconds:30} * 1000}",
            initialDelay = 60_000)
    public void deliverDue() {
        if (!outboxProperties.enabled()) {
            return;
        }
        Instant now = clock.instant();
        List<OutboxMessage> due;
        try {
            due = outboxMessageRepository.claimDue(now, now.plus(LEASE), outboxProperties.batch());
        } catch (Exception e) {
            log.warn("Outbox: claiming due messages failed: {}", e.getMessage());
            return;
        }
        for (OutboxMessage message : due) {
            try {
                deliver(message);
            } catch (Exception e) {
                // Its lease runs out and it is claimed again; the rest of the batch goes on.
                log.warn("Outbox: delivery of message {} failed: {}", message.id(), e.getMessage());
            }
        }
    }

    /** One attempt at a claimed row, and the state it leaves the row in. */
    private void deliver(OutboxMessage message) {
        if (!crmUseCase.isConnected(message.companyId())) {
            // The company took its CRM away after the write was queued: nothing will ever take
            // it, and that is the company's choice, not a fault to alert anyone about.
            write(message.markDead("company has no CRM connection"));
            return;
        }
        String reference;
        String error;
        try {
            reference = send(message);
            error = reference == null ? "the CRM did not accept the write" : null;
        } catch (RuntimeException e) {
            reference = null;
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
        Instant now = clock.instant();
        if (error == null) {
            write(message.markSent(now));
            if (message.kind() == OutboxMessageKind.CRM_NOTE) {
                recordNoteReference(message, reference);
            }
            log.info("Outbox: {} for call {} delivered (attempt {})",
                    message.kind(), message.callAttemptId(), message.attempts());
        } else if (message.attempts() >= outboxProperties.maxAttempts()) {
            write(message.markDead(error));
            raiseDeadAlert(message, error);
        } else {
            Instant next = now.plus(backoffAfter(message.attempts()));
            write(message.scheduleRetry(next, error));
            log.warn("Outbox: {} for call {} failed on attempt {} ({}); next try at {}",
                    message.kind(), message.callAttemptId(), message.attempts(), error, next);
        }
    }

    /**
     * @return what the CRM filed the write under — the note's id, or the call's own id for the
     *         call history — or {@code null} when it did not take it
     */
    private String send(OutboxMessage message) {
        Map<String, Object> payload = message.payload();
        long leadId = ((Number) payload.get(LEAD_ID)).longValue();
        return switch (message.kind()) {
            case CRM_NOTE -> crmUseCase.postNote(message.companyId(), leadId, (String) payload.get(NOTE_TEXT));
            case CRM_CALL_HISTORY -> {
                String callUuid = (String) payload.get(CALL_UUID);
                boolean accepted = crmUseCase.postCallHistory(message.companyId(), leadId, callUuid,
                        Instant.parse((String) payload.get(STARTED_AT)),
                        ((Number) payload.get(DURATION_SECONDS)).intValue(),
                        Boolean.TRUE.equals(payload.get(INBOUND)), Boolean.TRUE.equals(payload.get(ANSWERED)),
                        (String) payload.get(CLIENT_PHONE), null);
                yield accepted ? callUuid : null;
            }
        };
    }

    private void write(OutboxMessage message) {
        if (!outboxMessageRepository.update(message)) {
            log.warn("Outbox: message {} was claimed again during its delivery; {} not written",
                    message.id(), message.status());
        }
    }

    /** The configured backoff after the first failed attempt, doubled for each one after it; at most an hour. */
    private Duration backoffAfter(int attempts) {
        int doublings = Math.min(Math.max(attempts - 1, 0), 20);
        Duration backoff = Duration.ofSeconds(outboxProperties.backoffSeconds()).multipliedBy(1L << doublings);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    /** The id the CRM gave the note, onto the call's result — the calls page and reports read it there. */
    private void recordNoteReference(OutboxMessage message, String reference) {
        try {
            callResultRepository.markCrmPosted(message.callAttemptId(), reference);
        } catch (Exception e) {
            log.warn("Outbox: note id not stored on call {}: {}", message.callAttemptId(), e.getMessage());
        }
    }

    /**
     * A write nothing will try again: an ALERT line for the operations log sink, as
     * {@code AlertingService} raises, and a notification to the company whose CRM is now
     * missing it.
     */
    private void raiseDeadAlert(OutboxMessage message, String error) {
        log.error("ALERT: outbox message {} ({} for call {}) gave up after {} attempts: {}",
                message.id(), message.kind(), message.callAttemptId(), message.attempts(), error);
        try {
            notificationUseCase.notify(message.companyId(), NotificationType.ERROR_OCCURRED, "CRM'ga yozilmadi",
                    "Qo'ng'iroq (ID: " + message.callAttemptId() + ") " + describe(message.kind()) + " "
                            + message.attempts() + " urinishdan keyin ham CRM'ga yozilmadi", null);
        } catch (Exception e) {
            log.warn("Outbox: notification about dead message {} failed: {}", message.id(), e.getMessage());
        }
    }

    private static String describe(OutboxMessageKind kind) {
        return switch (kind) {
            case CRM_NOTE -> "xulosasi";
            case CRM_CALL_HISTORY -> "qo'ng'iroqlar tarixidagi yozuvi";
        };
    }
}
