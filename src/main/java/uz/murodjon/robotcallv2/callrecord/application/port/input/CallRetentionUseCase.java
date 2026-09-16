package uz.murodjon.robotcallv2.callrecord.application.port.input;

import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;

import java.time.Instant;
import java.util.List;

/**
 * What the retention sweep ({@code RetentionService}) asks of the call records.
 *
 * <p>Every method spans all companies, as the sweep does, and touches at most
 * {@code batchSize} rows, so the caller loops in short transactions instead of holding a
 * lock on a busy table for the whole history.
 */
public interface CallRetentionUseCase {

    /** Deletes transcript lines of conversations past their retention; returns how many went. */
    int deleteExpiredTranscripts(ConversationRetention retention, int batchSize);

    /**
     * The stored recordings of calls whose conversation is past its retention. Deleting the
     * {@code stored_file} row is what clears the call's link to it, so a recording that is
     * removed does not come back in the next batch.
     */
    List<Long> findExpiredRecordingFileIds(ConversationRetention retention, int batchSize);

    /** Deletes {@code call_result} rows written before {@code cutoff}; returns how many went. */
    int deleteResultsCreatedBefore(Instant cutoff, int batchSize);

    /**
     * Deletes outbox messages that are done with — SENT or DEAD — created before
     * {@code cutoff}; a PENDING message is never deleted. Returns how many went.
     */
    int deleteFinishedOutboxMessagesBefore(Instant cutoff, int batchSize);
}
