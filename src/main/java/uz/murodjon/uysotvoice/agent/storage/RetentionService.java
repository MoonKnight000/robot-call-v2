package uz.murodjon.uysotvoice.agent.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.rtp.RtpProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Stream;

/**
 * Enforces the recording retention period (PROJECT.md §11.3). Recordings are evidence
 * in a dispute, so they are kept for a configured window and then removed — from
 * object storage, from the local recording directory, and as transcript rows.
 *
 * <p>{@code call_attempt} and {@code call_result} survive: they carry the disposition
 * and summary that reporting is built on and hold no raw audio or verbatim speech.
 *
 * <p>Runs nightly and is best-effort — a failure is logged and retried on the next run.
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final AudioStorageService storage;
    private final AudioStorageProperties storageProps;
    private final JdbcTemplate jdbc;
    private final Path recordingDir;

    public RetentionService(AudioStorageService storage,
                            AudioStorageProperties storageProps,
                            JdbcTemplate jdbc,
                            RtpProperties rtpProps) {
        this.storage = storage;
        this.storageProps = storageProps;
        this.jdbc = jdbc;
        this.recordingDir = Path.of(rtpProps.recordingDir());
    }

    /** 03:30 — outside the 09:00–20:00 dial window, so it never competes with live calls. */
    @Scheduled(cron = "${voice-agent.storage.retention-cron:0 30 3 * * *}")
    public void purge() {
        int days = storageProps.retentionDays();
        if (days <= 0) {
            return; // retention disabled: keep everything
        }
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        List<String> removedObjects = storage.deleteOlderThan(days);
        int localFiles = purgeLocalRecordings(cutoff);
        int transcripts = purgeTranscripts(days);
        int urls = clearRecordingUrls(days);

        if (!removedObjects.isEmpty() || localFiles > 0 || transcripts > 0) {
            log.info("Retention ({} days): {} object(s), {} local file(s), {} transcript row(s), {} url(s) cleared",
                    days, removedObjects.size(), localFiles, transcripts, urls);
        }
    }

    /** Local WAVs that were never uploaded (storage off or upload failed) still age out. */
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

    /** Verbatim speech goes with the audio; the summary in call_result stays. */
    private int purgeTranscripts(int days) {
        try {
            return jdbc.update(
                    "DELETE FROM call_transcript WHERE call_id IN ("
                            + "  SELECT id FROM call_attempt "
                            + "  WHERE ended_at IS NOT NULL AND ended_at < now() - (? * interval '1 day'))",
                    days);
        } catch (Exception e) {
            log.warn("Transcript purge failed: {}", e.getMessage());
            return 0;
        }
    }

    /** A URL pointing at a deleted object is worse than no URL. */
    private int clearRecordingUrls(int days) {
        try {
            return jdbc.update(
                    "UPDATE call_attempt SET recording_url = NULL "
                            + "WHERE recording_url IS NOT NULL AND ended_at IS NOT NULL "
                            + "  AND ended_at < now() - (? * interval '1 day')",
                    days);
        } catch (Exception e) {
            log.warn("Recording-url cleanup failed: {}", e.getMessage());
            return 0;
        }
    }
}
