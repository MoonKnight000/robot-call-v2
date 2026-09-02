package uz.murodjon.robotcallv2.storage.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallTranscriptJpaRepository;
import uz.murodjon.robotcallv2.storage.application.port.output.ObjectStoragePort;
import uz.murodjon.robotcallv2.storage.application.port.output.StoredFileRepository;
import uz.murodjon.robotcallv2.storage.domain.entity.StoredFile;
import uz.murodjon.robotcallv2.storage.domain.enums.FileCategory;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * Enforces the recording retention period.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final StoredFileRepository storedFiles;
    private final ObjectStoragePort objectStorage;
    private final AudioStorageProperties storageProps;
    private final CallTranscriptJpaRepository transcripts;
    private final Path recordingDir;

    public RetentionService(StoredFileRepository storedFiles, ObjectStoragePort objectStorage,
                            AudioStorageProperties storageProps, CallTranscriptJpaRepository transcripts,
                            RtpProperties rtpProps) {
        this.storedFiles = storedFiles;
        this.objectStorage = objectStorage;
        this.storageProps = storageProps;
        this.transcripts = transcripts;
        this.recordingDir = Path.of(rtpProps.recordingDir());
    }

    @Scheduled(cron = "${voice-agent.storage.retention-cron:0 30 3 * * *}")
    public void purge() {
        int days = storageProps.retentionDays();
        if (days <= 0) {
            return;
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

    private int purgeTranscripts(Instant cutoff) {
        try {
            return transcripts.purgeForAttemptsEndedBefore(cutoff);
        } catch (Exception e) {
            log.warn("Transcript purge failed: {}", e.getMessage());
            return 0;
        }
    }
}
