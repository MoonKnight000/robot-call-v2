package uz.murodjon.uysotvoice.storage.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.rtp.RtpProperties;
import uz.murodjon.uysotvoice.callrecord.repository.CallTranscriptJpaRepository;
import uz.murodjon.uysotvoice.storage.config.AudioStorageProperties;
import uz.murodjon.uysotvoice.storage.dto.StoredFile;
import uz.murodjon.uysotvoice.storage.enums.FileCategory;
import uz.murodjon.uysotvoice.storage.repository.StoredFileRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * Enforces the recording retention period (PROJECT.md §11.3). Recordings are evidence
 * in a dispute, so they are kept for a configured window and then removed — from MinIO,
 * from the {@code stored_file} catalog, from the local recording directory, and as
 * transcript rows.
 *
 * <p>{@code call_attempt} and {@code call_result} survive: they carry the disposition
 * and summary that reporting is built on and hold no raw audio or verbatim speech.
 * {@code call_attempt.recording_file_id} clears itself ({@code ON DELETE SET NULL}) the
 * moment its {@code stored_file} row is deleted below — no separate cleanup query needed.
 *
 * <p>Runs nightly and is best-effort — a failure is logged and retried on the next run.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final StoredFileRepository storedFiles;
    private final ObjectStorageService objectStorage;
    private final AudioStorageProperties storageProps;
    private final CallTranscriptJpaRepository transcripts;
    private final Path recordingDir;

    public RetentionService(StoredFileRepository storedFiles, ObjectStorageService objectStorage,
                            AudioStorageProperties storageProps, CallTranscriptJpaRepository transcripts,
                            RtpProperties rtpProps) {
        this.storedFiles = storedFiles;
        this.objectStorage = objectStorage;
        this.storageProps = storageProps;
        this.transcripts = transcripts;
        this.recordingDir = Path.of(rtpProps.recordingDir());
    }

    /**
     * 03:30 — outside the 09:00–20:00 dial window, so it never competes with live calls.
     */
    @Scheduled(cron = "${voice-agent.storage.retention-cron:0 30 3 * * *}")
    public void purge() {
        int days = storageProps.retentionDays();
        if (days <= 0) {
            return; // retention disabled: keep everything
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        int removedObjects = purgeStoredRecordings(cutoff);
        int localFiles = purgeLocalRecordings(cutoff);
        int transcriptRows = purgeTranscripts(cutoff);

        if (removedObjects > 0 || localFiles > 0 || transcriptRows > 0) {
            log.info("Retention ({} days): {} object(s), {} local file(s), {} transcript row(s)",
                    days, removedObjects, localFiles, transcriptRows);
        }
    }

    /** Every {@code stored_file} of category AUDIO older than {@code cutoff} — MinIO object, then catalog row. */
    private int purgeStoredRecordings(Instant cutoff) {
        List<StoredFile> old = storedFiles.findOlderThan(FileCategory.AUDIO, cutoff);
        int removed = 0;
        for (StoredFile file : old) {
            try {
                objectStorage.delete(file.bucket(), file.path());
                storedFiles.delete(file.id());
                removed++;
            } catch (Exception e) {
                log.warn("Could not remove stored recording {}: {}", file.id(), e.getMessage());
            }
        }
        return removed;
    }

    /**
     * Local WAVs that were never uploaded (storage off or upload failed) still age out.
     */
    private int purgeLocalRecordings(Instant cutoff) {
        if (!Files.isDirectory(recordingDir)) {
            return 0;
        }
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

    /**
     * Verbatim speech goes with the audio; the summary in call_result stays.
     */
    private int purgeTranscripts(Instant cutoff) {
        try {
            return transcripts.purgeForAttemptsEndedBefore(cutoff);
        } catch (Exception e) {
            log.warn("Transcript purge failed: {}", e.getMessage());
            return 0;
        }
    }
}
