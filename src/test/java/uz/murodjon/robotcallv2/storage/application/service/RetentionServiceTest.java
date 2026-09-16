package uz.murodjon.robotcallv2.storage.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallRetentionUseCase;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.storage.application.port.output.ObjectStoragePort;
import uz.murodjon.robotcallv2.storage.application.port.output.StoredFileRepository;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.config.RetentionProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RetentionServiceTest {

    @TempDir
    Path recordingDir;

    private final StoredFileRepository storedFileRepository = mock(StoredFileRepository.class);
    private final ObjectStoragePort objectStorage = mock(ObjectStoragePort.class);
    private final CallRetentionUseCase callRetention = mock(CallRetentionUseCase.class);
    private final ClientMemoryUseCase clientMemory = mock(ClientMemoryUseCase.class);
    private final AuditUseCase audit = mock(AuditUseCase.class);
    private final ClusterJobLock clusterJobLock = mock(ClusterJobLock.class);

    private RetentionService service(int conversationDays, int summaryDays, int clientMemoryDays, int auditDays,
                                     int batchSize) {
        RtpProperties rtpProperties = mock(RtpProperties.class);
        when(rtpProperties.recordingDir()).thenReturn(recordingDir.toString());
        // Outbox at its shipped default: 30 days.
        RetentionProperties retentionProperties = new RetentionProperties("0 30 3 * * *", conversationDays,
                Duration.ofHours(1), summaryDays, clientMemoryDays, auditDays, 30, batchSize);
        return new RetentionService(storedFileRepository, objectStorage, retentionProperties, callRetention,
                clientMemory, audit, rtpProperties, clusterJobLock);
    }

    private void lockIsFree() {
        when(clusterJobLock.runExclusively(anyString(), any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return true;
        });
    }

    /** Summaries, memory and the audit log were never deleted before: nothing goes until the owner sets a period. */
    @Test
    void leavesSummariesMemoryAndAuditAloneByDefault() {
        lockIsFree();
        when(callRetention.findExpiredRecordingFileIds(any(), anyInt())).thenReturn(List.of());
        when(storedFileRepository.findOlderThanExceptCallRecordings(any(), any(), anyInt())).thenReturn(List.of());

        service(90, 0, 0, 0, 100).purge();

        verify(callRetention).deleteExpiredTranscripts(any(), eq(100));
        verify(callRetention, never()).deleteResultsCreatedBefore(any(), anyInt());
        verify(clientMemory, never()).deleteNotUpdatedSince(any(), anyInt());
        verify(audit, never()).deleteCreatedBefore(any(), anyInt());
    }

    /** Finished outbox rows are bookkeeping, not personal data: they go after 30 days without opting in. */
    @Test
    void purgesFinishedOutboxMessagesByDefault() {
        lockIsFree();
        when(callRetention.findExpiredRecordingFileIds(any(), anyInt())).thenReturn(List.of());
        when(storedFileRepository.findOlderThanExceptCallRecordings(any(), any(), anyInt())).thenReturn(List.of());
        Instant before = Instant.now();

        service(90, 0, 0, 0, 100).purge();

        verify(callRetention).deleteFinishedOutboxMessagesBefore(
                argThat(cutoff -> !cutoff.isAfter(before.minus(30, ChronoUnit.DAYS).plusSeconds(60))), eq(100));
    }

    @Test
    void deletesInBatchesUntilOneComesBackShort() {
        lockIsFree();
        when(callRetention.findExpiredRecordingFileIds(any(), anyInt())).thenReturn(List.of());
        when(storedFileRepository.findOlderThanExceptCallRecordings(any(), any(), anyInt())).thenReturn(List.of());
        when(callRetention.deleteResultsCreatedBefore(any(), eq(2))).thenReturn(2, 2, 1);
        Instant before = Instant.now();

        service(90, 30, 0, 0, 2).purge();

        verify(callRetention, times(3)).deleteResultsCreatedBefore(
                argThat(cutoff -> !cutoff.isAfter(before.minus(30, ChronoUnit.DAYS).plusSeconds(60))), eq(2));
    }

    /** An expired call's recording goes from the object store first, then from the catalog. */
    @Test
    void removesAnExpiredCallRecordingThroughItsCall() throws Exception {
        lockIsFree();
        StoredFile recording = new StoredFile(7L, 1L, FileCategory.AUDIO, "call.wav", "com-1/audio/7.wav",
                "files", "audio/wav", 10, Instant.now());
        when(callRetention.findExpiredRecordingFileIds(any(), anyInt())).thenReturn(List.of(7L));
        when(storedFileRepository.find(7L)).thenReturn(recording);
        when(storedFileRepository.findOlderThanExceptCallRecordings(any(), any(), anyInt())).thenReturn(List.of());

        service(90, 0, 0, 0, 100).purge();

        var order = inOrder(objectStorage, storedFileRepository);
        order.verify(objectStorage).delete("files", "com-1/audio/7.wav");
        order.verify(storedFileRepository).delete(7L);
    }

    /** A batch in which nothing could be removed would come back unchanged — the sweep must not spin on it. */
    @Test
    void stopsWhenNothingInABatchCouldBeRemoved() throws Exception {
        lockIsFree();
        StoredFile recording = new StoredFile(7L, 1L, FileCategory.AUDIO, "call.wav", "p", "files", "audio/wav", 10,
                Instant.now());
        when(callRetention.findExpiredRecordingFileIds(any(), anyInt())).thenReturn(List.of(7L));
        when(storedFileRepository.find(7L)).thenReturn(recording);
        doThrow(new IllegalStateException("minio down")).when(objectStorage).delete(anyString(), anyString());
        when(storedFileRepository.findOlderThanExceptCallRecordings(any(), any(), anyInt())).thenReturn(List.of());

        service(90, 0, 0, 0, 1).purge();

        verify(callRetention, times(1)).findExpiredRecordingFileIds(any(), eq(1));
        verify(storedFileRepository, never()).delete(anyLong());
    }

    /** Each node's recording directory is its own: it is swept even when another node holds the lock. */
    @Test
    void sweepsLocalRecordingsOnEveryNodeOutsideTheLock() throws Exception {
        when(clusterJobLock.runExclusively(anyString(), any())).thenReturn(false);
        Path old = Files.createFile(recordingDir.resolve("old.wav"));
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(100, ChronoUnit.DAYS)));
        Path fresh = Files.createFile(recordingDir.resolve("fresh.wav"));

        service(90, 0, 0, 0, 100).purge();

        assertThat(old).doesNotExist();
        assertThat(fresh).exists();
        verifyNoInteractions(callRetention, storedFileRepository, objectStorage);
    }

    /** 0 conversation days keeps what no agent owns — voicemail and local files — forever. */
    @Test
    void keepsAudioNoCallOwnsWhenConversationDaysIsZero() throws Exception {
        lockIsFree();
        when(callRetention.findExpiredRecordingFileIds(any(), anyInt())).thenReturn(List.of());
        Path old = Files.createFile(recordingDir.resolve("old.wav"));
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(1000, ChronoUnit.DAYS)));

        service(0, 0, 0, 0, 100).purge();

        assertThat(old).exists();
        verify(storedFileRepository, never()).findOlderThanExceptCallRecordings(any(), any(), anyInt());
    }
}
