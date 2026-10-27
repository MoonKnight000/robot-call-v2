package uz.murodjon.robotcallv2.callrecord.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.callrecord.application.port.input.CallRetentionUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallAttemptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallEventRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallResultRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.CallTranscriptRepository;
import uz.murodjon.robotcallv2.callrecord.application.port.output.OutboxMessageRepository;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;

import java.time.Instant;
import java.util.List;

/** Removes call data that has been kept long enough (see {@link ConversationRetention}). */
@Service
public class CallRetentionService implements CallRetentionUseCase {

    private final CallTranscriptRepository callTranscriptRepository;
    private final CallAttemptRepository callAttemptRepository;
    private final CallResultRepository callResultRepository;
    private final OutboxMessageRepository outboxMessageRepository;
    private final CallEventRepository callEventRepository;

    public CallRetentionService(CallTranscriptRepository callTranscriptRepository,
                                CallAttemptRepository callAttemptRepository,
                                CallResultRepository callResultRepository,
                                OutboxMessageRepository outboxMessageRepository,
                                CallEventRepository callEventRepository) {
        this.callTranscriptRepository = callTranscriptRepository;
        this.callAttemptRepository = callAttemptRepository;
        this.callResultRepository = callResultRepository;
        this.outboxMessageRepository = outboxMessageRepository;
        this.callEventRepository = callEventRepository;
    }

    @Override
    public int deleteExpiredTranscripts(ConversationRetention retention, int batchSize) {
        return callTranscriptRepository.deleteExpired(retention, batchSize);
    }

    @Override
    public List<Long> findExpiredRecordingFileIds(ConversationRetention retention, int batchSize) {
        return callAttemptRepository.findExpiredRecordingFileIds(retention, batchSize);
    }

    @Override
    public int deleteResultsCreatedBefore(Instant cutoff, int batchSize) {
        return callResultRepository.deleteCreatedBefore(cutoff, batchSize);
    }

    @Override
    public int deleteFinishedOutboxMessagesBefore(Instant cutoff, int batchSize) {
        return outboxMessageRepository.deleteFinishedCreatedBefore(cutoff, batchSize);
    }

    @Override
    public int deleteEventsCreatedBefore(Instant cutoff, int batchSize) {
        return callEventRepository.deleteCreatedBefore(cutoff, batchSize);
    }
}
