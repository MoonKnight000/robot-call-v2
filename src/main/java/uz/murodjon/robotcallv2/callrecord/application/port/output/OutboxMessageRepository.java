package uz.murodjon.robotcallv2.callrecord.application.port.output;

import uz.murodjon.robotcallv2.callrecord.domain.entity.OutboxMessage;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface OutboxMessageRepository {

    /**
     * Stores a new PENDING message, due now — unless a message with the same idempotency key
     * exists already, in which case nothing is written. Joins the caller's transaction, so a
     * message can be stored together with the row it is about.
     *
     * @return the new message's id, or {@code null} when its key was already taken
     */
    Long enqueue(long companyId, OutboxMessage message);

    /**
     * Takes up to {@code limit} due PENDING messages for this worker, in a short transaction
     * of its own: each one's attempt is counted and it stays hidden from every worker until
     * {@code leaseUntil}. Rows another worker is claiming at the same moment are skipped, not
     * waited for.
     */
    List<OutboxMessage> claimDue(Instant now, Instant leaseUntil, int limit);

    /**
     * Takes one message no attempt has been made on yet — the delivery right after it was
     * enqueued — the same way {@link #claimDue} does.
     *
     * @return the claimed message, or {@code null} when a worker got there first
     */
    OutboxMessage claimFresh(long id, Instant leaseUntil);

    /**
     * Writes the delivery state the message now carries — only while the row is still
     * PENDING at the message's attempt count, so a worker whose lease ran out cannot overwrite
     * what a newer claim wrote.
     *
     * @return whether the state was written
     */
    boolean update(OutboxMessage message);

    /**
     * Deletes up to {@code batchSize} SENT or DEAD messages, across every company, created
     * before {@code cutoff}; PENDING ones are never touched. Returns how many went.
     */
    int deleteFinishedCreatedBefore(Instant cutoff, int batchSize);

    /**
     * Empties the payload of the company's PENDING and DEAD messages about calls placed to or
     * answered from one of {@code phones}, closing the PENDING ones as DEAD with
     * {@code reason}. Returns how many were emptied.
     */
    int erasePayloadsByPhones(long companyId, Collection<String> phones, String reason);
}
