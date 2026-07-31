package uz.murodjon.uysotvoice.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.UploadObjectArgs;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.storage.config.AudioStorageProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Uploads finished call recordings to MinIO/S3 and returns their URL (Stage 9).
 * Disabled by default — when off (or on any error) the recording stays on local
 * disk and {@link #upload} returns {@code null}, leaving {@code recording_url} empty.
 */
@Component
public class AudioStorageService {

    private static final Logger log = LoggerFactory.getLogger(AudioStorageService.class);

    private final AudioStorageProperties props;
    private volatile MinioClient client;

    public AudioStorageService(AudioStorageProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        if (!props.enabled()) {
            log.info("Audio storage disabled (voice-agent.storage.enabled=false)");
            return;
        }
        if (props.endpoint() == null || props.endpoint().isBlank()) {
            log.warn("Audio storage enabled but endpoint is blank; recordings will not be uploaded");
            return;
        }
        try {
            client = MinioClient.builder()
                    .endpoint(props.endpoint())
                    .credentials(props.accessKey(), props.secretKey())
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(props.bucket()).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(props.bucket()).build());
            }
            log.info("Audio storage ready (endpoint={}, bucket={})", props.endpoint(), props.bucket());
        } catch (Exception e) {
            log.error("Audio storage unavailable: {}", e.getMessage());
            client = null;
        }
    }

    /**
     * Upload {@code file} under {@code objectName}; returns the object URL or
     * {@code null} if storage is unavailable or the upload fails.
     */
    public String upload(Path file, String objectName) {
        MinioClient current = client;
        if (current == null || file == null) {
            return null;
        }
        try {
            current.uploadObject(UploadObjectArgs.builder()
                    .bucket(props.bucket())
                    .object(objectName)
                    .filename(file.toString())
                    .contentType("audio/wav")
                    .build());
            String base = props.endpoint().endsWith("/") ? props.endpoint() : props.endpoint() + "/";
            String url = base + props.bucket() + "/" + objectName;
            log.info("Uploaded recording {} -> {}", file, url);
            return url;
        } catch (Exception e) {
            log.warn("Recording upload failed for {}: {}", file, e.getMessage());
            return null;
        }
    }

    /**
     * Delete the local WAV now that object storage holds it. Call only after a
     * successful {@link #upload} — otherwise this throws away the only copy.
     * No-op when {@code delete-local-after-upload} is off.
     */
    public void deleteLocalCopy(Path file) {
        if (!props.deleteLocalAfterUpload() || file == null) {
            return;
        }
        try {
            if (Files.deleteIfExists(file)) {
                log.debug("Deleted local copy of {}", file);
            }
        } catch (Exception e) {
            log.warn("Could not delete local recording {}: {}", file, e.getMessage());
        }
    }

    /**
     * Remove stored recordings older than {@code retentionDays} (§11.3). Returns the
     * object names removed so the caller can purge the matching transcripts.
     */
    public List<String> deleteOlderThan(int retentionDays) {
        MinioClient current = client;
        if (current == null || retentionDays <= 0) {
            return List.of();
        }
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(retentionDays);
        List<String> removed = new ArrayList<>();
        try {
            Iterable<Result<Item>> items = current.listObjects(
                    ListObjectsArgs.builder().bucket(props.bucket()).recursive(true).build());
            for (Result<Item> result : items) {
                Item item = result.get();
                if (item.lastModified() == null || item.lastModified().isAfter(cutoff)) {
                    continue;
                }
                current.removeObject(RemoveObjectArgs.builder()
                        .bucket(props.bucket())
                        .object(item.objectName())
                        .build());
                removed.add(item.objectName());
            }
        } catch (Exception e) {
            log.warn("Retention sweep failed: {}", e.getMessage());
        }
        if (!removed.isEmpty()) {
            log.info("Retention: removed {} recording(s) older than {} days", removed.size(), retentionDays);
        }
        return removed;
    }
}
