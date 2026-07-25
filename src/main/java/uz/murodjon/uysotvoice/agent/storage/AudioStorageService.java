package uz.murodjon.uysotvoice.agent.storage;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.UploadObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

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
}
