package uz.murodjon.robotcallv2.storage.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallRetentionUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.ConversationRetention;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;
import uz.murodjon.robotcallv2.storage.application.port.output.ObjectStoragePort;
import uz.murodjon.robotcallv2.storage.application.port.output.StoredFileRepository;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.config.RetentionProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

/**
 * Enforces how long personal data is kept ({@link RetentionProperties}).
 *
 * <p>A call's conversation — transcript and recording — follows the agent that ran it
 * ({@link ConversationRetention}); audio no call owns (voicemail, recordings left on local
 * disk) follows {@code conversationDays}. Summaries, client memory and the audit log are
 * purged only once their own period is set: those purges remove what was never removed
 * before, so they wait for the owner. Finished outbox messages are delivery bookkeeping and
 * go after {@code outboxDays} by default.
 *
 * <p>Every delete takes at most {@code batchSize} rows in its own short transaction, so a
 * first run over years of history does not hold a busy table for minutes.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final StoredFileRepository storedFileRepository;
    private final ObjectStoragePort objectStorage;
    private final RetentionProperties retentionProperties;
    private final CallRetentionUseCase callRetentionUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final ChatUseCase chatUseCase;
    private final AuditUseCase auditUseCase;
    private final Path recordingDir;
    private final ClusterJobLock clusterJobLock;

    public RetentionService(StoredFileRepository storedFileRepository, ObjectStoragePort objectStorage,
                            RetentionProperties retentionProperties,
                            CallRetentionUseCase callRetentionUseCase,
                            ClientMemoryUseCase clientMemoryUseCase,
                            ChatUseCase chatUseCase,
                            AuditUseCase auditUseCase,
                            RtpProperties rtpProperties, ClusterJobLock clusterJobLock) {
        this.storedFileRepository = storedFileRepository;
        this.objectStorage = objectStorage;
        this.retentionProperties = retentionProperties;
        this.callRetentionUseCase = callRetentionUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.chatUseCase = chatUseCase;
        this.auditUseCase = auditUseCase;
        this.recordingDir = Path.of(rtpProperties.recordingDir());
        this.clusterJobLock = clusterJobLock;
    }

    /**
     * The local sweep runs on every node, outside the lock: each node's recording directory
     * is its own, and only that node can clear it. What lives in the database and the
     * object store is shared, so one node at a time purges it.
     */
    @Scheduled(cron = "${voice-agent.retention.cron:0 30 3 * * *}")
    public void purge() {
        int localFiles = purgeLocalRecordings();
        if (localFiles > 0) {
            log.info("Retention: {} local recording file(s)", localFiles);
        }
        clusterJobLock.runExclusively("storage.retention", this::purgeExpired);
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        int batchSize = retentionProperties.batchSize();
        ConversationRetention conversations = ConversationRetention.of(now,
                retentionProperties.conversationDays(), retentionProperties.zeroPiiGrace());

        int recordings = removeFilesInBatches("call recording", batchSize,
                () -> callRetentionUseCase.findExpiredRecordingFileIds(conversations, batchSize).stream()
                        .map(storedFileRepository::find)
                        .filter(Objects::nonNull)
                        .toList());
        int transcripts = deleteInBatches("transcript", batchSize,
                () -> callRetentionUseCase.deleteExpiredTranscripts(conversations, batchSize));
        int otherAudio = retentionProperties.conversationDays() <= 0 ? 0
                : removeFilesInBatches("audio file", batchSize,
                        () -> storedFileRepository.findOlderThanExceptCallRecordings(FileCategory.AUDIO,
                                conversations.defaultCutoff(), batchSize));
        int summaries = deleteOlderThan("call summary", retentionProperties.summaryDays(), now, batchSize,
                cutoff -> callRetentionUseCase.deleteResultsCreatedBefore(cutoff, batchSize));
        int memories = deleteOlderThan("client memory", retentionProperties.clientMemoryDays(), now, batchSize,
                cutoff -> clientMemoryUseCase.deleteNotUpdatedSince(cutoff, batchSize));
        // A chat is a conversation the agent held, so it keeps a conversation's days
        // (UNIVERSAL-OPERATOR-PLAN E.4): a transcript typed into a widget is the same
        // personal data as one spoken into a phone, and nothing else would ever delete it.
        int chats = deleteOlderThan("chat", retentionProperties.conversationDays(), now, batchSize,
                cutoff -> chatUseCase.deleteSessionsCreatedBefore(cutoff, batchSize));
        int auditRows = deleteOlderThan("audit log", retentionProperties.auditDays(), now, batchSize,
                cutoff -> auditUseCase.deleteCreatedBefore(cutoff, batchSize));
        int outboxRows = deleteOlderThan("outbox message", retentionProperties.outboxDays(), now, batchSize,
                cutoff -> callRetentionUseCase.deleteFinishedOutboxMessagesBefore(cutoff, batchSize));

        if (recordings + transcripts + otherAudio + summaries + memories + auditRows + outboxRows + chats > 0) {
            log.info("Retention: {} call recording(s), {} transcript line(s), {} other audio file(s), "
                            + "{} call summary(ies), {} client memory row(s), {} audit row(s), "
                            + "{} finished outbox message(s), {} chat(s)",
                    recordings, transcripts, otherAudio, summaries, memories, auditRows, outboxRows, chats);
        }
    }

    /** Nothing when {@code days} is 0 or less — keep forever, the default for these purges. */
    private int deleteOlderThan(String what, int days, Instant now, int batchSize, ToIntFunction<Instant> batch) {
        if (days <= 0) {
            return 0;
        }
        Instant cutoff = now.minus(days, ChronoUnit.DAYS);
        return deleteInBatches(what, batchSize, () -> batch.applyAsInt(cutoff));
    }

    /** Repeats {@code batch} until one comes back short; a failure stops this purge, not the others. */
    private int deleteInBatches(String what, int batchSize, IntSupplier batch) {
        int deleted = 0;
        try {
            int deletedInBatch;
            do {
                deletedInBatch = batch.getAsInt();
                deleted += deletedInBatch;
            } while (deletedInBatch >= batchSize);
        } catch (Exception e) {
            log.warn("{} purge failed after {} row(s): {}", what, deleted, e.getMessage());
        }
        return deleted;
    }

    /**
     * Removes each file — the object, then its catalog row — batch after batch. Stops at a
     * short batch, and at one in which nothing could be removed: that batch would only come
     * back unchanged.
     */
    private int removeFilesInBatches(String what, int batchSize, Supplier<List<StoredFile>> batch) {
        int removed = 0;
        try {
            List<StoredFile> files;
            int removedInBatch;
            do {
                files = batch.get();
                removedInBatch = 0;
                for (StoredFile file : files) {
                    if (remove(file)) {
                        removedInBatch++;
                    }
                }
                removed += removedInBatch;
            } while (files.size() >= batchSize && removedInBatch > 0);
        } catch (Exception e) {
            log.warn("{} purge failed after {} file(s): {}", what, removed, e.getMessage());
        }
        return removed;
    }

    private boolean remove(StoredFile file) {
        try {
            objectStorage.delete(file.bucket(), file.path());
            storedFileRepository.delete(file.id());
            return true;
        } catch (Exception e) {
            log.warn("Could not remove stored file {}: {}", file.id(), e.getMessage());
            return false;
        }
    }

    /**
     * Recordings left on this node's disk — an upload that failed, or local copies kept on
     * purpose — after {@code conversationDays}. Nothing when that is 0 or less.
     */
    private int purgeLocalRecordings() {
        if (retentionProperties.conversationDays() <= 0 || !Files.isDirectory(recordingDir)) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(retentionProperties.conversationDays(), ChronoUnit.DAYS);
        int deleted = 0;
        try (Stream<Path> files = Files.list(recordingDir)) {
            for (Path file : files.toList()) {
                try {
                    if (!file.toString().endsWith(".wav")
                            || Files.getLastModifiedTime(file).toInstant().isAfter(cutoff)) {
                        continue;
                    }
                    if (Files.deleteIfExists(file)) {
                        deleted++;
                    }
                } catch (IOException e) {
                    log.debug("Could not purge {}: {}", file, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Local recording sweep failed: {}", e.getMessage());
        }
        return deleted;
    }
}
