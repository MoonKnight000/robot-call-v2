package uz.murodjon.robotcallv2.callrecord.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callrecord.application.port.output.*;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CallRetentionServiceTest {

    private CallTranscriptRepository callTranscriptRepository;
    private CallAttemptRepository callAttemptRepository;
    private CallResultRepository callResultRepository;
    private OutboxMessageRepository outboxMessageRepository;
    private CallEventRepository callEventRepository;

    private CallRetentionService service;

    @BeforeEach
    void setUp() {
        callTranscriptRepository = mock(CallTranscriptRepository.class);
        callAttemptRepository = mock(CallAttemptRepository.class);
        callResultRepository = mock(CallResultRepository.class);
        outboxMessageRepository = mock(OutboxMessageRepository.class);
        callEventRepository = mock(CallEventRepository.class);

        service = new CallRetentionService(
                callTranscriptRepository,
                callAttemptRepository,
                callResultRepository,
                outboxMessageRepository,
                callEventRepository
        );
    }

    @Test
    void delegatesDeleteEventsCreatedBeforeToRepository() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(90));
        when(callEventRepository.deleteCreatedBefore(cutoff, 100)).thenReturn(25);

        int deleted = service.deleteEventsCreatedBefore(cutoff, 100);

        assertThat(deleted).isEqualTo(25);
        verify(callEventRepository).deleteCreatedBefore(cutoff, 100);
    }

    @Test
    void delegatesDeleteExpiredTranscriptsToRepository() {
        ConversationRetention retention = ConversationRetention.of(Instant.now(), 90, Duration.ofHours(1));
        when(callTranscriptRepository.deleteExpired(retention, 50)).thenReturn(10);

        int deleted = service.deleteExpiredTranscripts(retention, 50);

        assertThat(deleted).isEqualTo(10);
        verify(callTranscriptRepository).deleteExpired(retention, 50);
    }

    @Test
    void delegatesFindExpiredRecordingFileIdsToRepository() {
        ConversationRetention retention = ConversationRetention.of(Instant.now(), 90, Duration.ofHours(1));
        when(callAttemptRepository.findExpiredRecordingFileIds(retention, 50)).thenReturn(List.of(1L, 2L));

        List<Long> ids = service.findExpiredRecordingFileIds(retention, 50);

        assertThat(ids).containsExactly(1L, 2L);
        verify(callAttemptRepository).findExpiredRecordingFileIds(retention, 50);
    }

    @Test
    void delegatesDeleteResultsCreatedBeforeToRepository() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        when(callResultRepository.deleteCreatedBefore(cutoff, 50)).thenReturn(5);

        int deleted = service.deleteResultsCreatedBefore(cutoff, 50);

        assertThat(deleted).isEqualTo(5);
        verify(callResultRepository).deleteCreatedBefore(cutoff, 50);
    }

    @Test
    void delegatesDeleteFinishedOutboxMessagesBeforeToRepository() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        when(outboxMessageRepository.deleteFinishedCreatedBefore(cutoff, 50)).thenReturn(8);

        int deleted = service.deleteFinishedOutboxMessagesBefore(cutoff, 50);

        assertThat(deleted).isEqualTo(8);
        verify(outboxMessageRepository).deleteFinishedCreatedBefore(cutoff, 50);
    }
}
