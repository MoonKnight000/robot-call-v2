package uz.murodjon.uysotvoice.storage.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.UploadObjectArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.storage.config.AudioStorageProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The MinIO/S3 connection and bucket/object mechanics shared by every object-storage
 * use in this project (call recordings, company logos, user avatars, documents) — one
 * MinIO deployment, one client, one shared bucket, object keys namespaced per company/
 * category by the caller. {@link uz.murodjon.uysotvoice.storage.service.FileStorageService}
 * is the only caller — it owns the {@code stored_file} catalog row that makes an object
 * key meaningful; this class only knows bytes in, bytes out.
 */
@Component
public class ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(ObjectStorageService.class);

    private final AudioStorageProperties props;
    private volatile MinioClient client;
    private final Set<String> knownBuckets = ConcurrentHashMap.newKeySet();

    public ObjectStorageService(AudioStorageProperties props) {
        this.props = props;
    }

    @PostConstruct
    public void init() {
        if (!props.enabled()) {
            log.info("Object storage disabled (voice-agent.storage.enabled=false)");
            return;
        }
        if (props.endpoint() == null || props.endpoint().isBlank()) {
            log.warn("Object storage enabled but endpoint is blank; uploads will not happen");
            return;
        }
        try {
            client = MinioClient.builder()
                    .endpoint(props.endpoint())
                    .credentials(props.accessKey(), props.secretKey())
                    .build();
            log.info("Object storage ready (endpoint={})", props.endpoint());
        } catch (Exception e) {
            log.error("Object storage unavailable: {}", e.getMessage());
            client = null;
        }
    }

    public boolean available() {
        return client != null;
    }

    /**
     * Upload {@code data} under {@code objectName} in {@code bucket} (created on first
     * use if missing); returns whether it succeeded. Callers decide whether failure is
     * fatal (recordings tolerate it, see {@code AudioStorageService}) or should surface
     * as an error to the caller (image uploads — see {@code company}/{@code profile}
     * controllers).
     */
    public boolean upload(byte[] data, String bucket, String objectName, String contentType) {
        MinioClient current = client;
        if (current == null || data == null) {
            return false;
        }
        try {
            ensureBucket(current, bucket);
            current.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            log.info("Uploaded {}/{} ({} bytes)", bucket, objectName, data.length);
            return true;
        } catch (Exception e) {
            log.warn("Upload failed for {}/{}: {}", bucket, objectName, e.getMessage());
            return false;
        }
    }

    /**
     * As {@link #upload(byte[], String, String, String)}, streaming from a local file
     * rather than buffering it into memory first — used for call recordings, which can
     * run larger than what's comfortable to hold as a byte array.
     */
    public boolean uploadFile(Path file, String bucket, String objectName, String contentType) {
        MinioClient current = client;
        if (current == null || file == null) {
            return false;
        }
        try {
            ensureBucket(current, bucket);
            current.uploadObject(UploadObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .filename(file.toString())
                    .contentType(contentType)
                    .build());
            log.info("Uploaded {} -> {}/{}", file, bucket, objectName);
            return true;
        } catch (Exception e) {
            log.warn("Upload failed for {}: {}", file, e.getMessage());
            return false;
        }
    }

    /** Streams {@code bucket}/{@code objectName} back — the caller (FileStorageService) closes it. */
    public InputStream download(String bucket, String objectName) throws Exception {
        MinioClient current = client;
        if (current == null) {
            throw new IOException("object storage unavailable");
        }
        return current.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
    }

    /** Removes a single object — used by the retention sweep once it has dropped the {@code stored_file} row. */
    public void delete(String bucket, String objectName) throws Exception {
        MinioClient current = client;
        if (current == null) {
            return;
        }
        current.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }

    private void ensureBucket(MinioClient current, String bucket) throws Exception {
        if (knownBuckets.contains(bucket)) {
            return;
        }
        boolean exists = current.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            current.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        knownBuckets.add(bucket);
    }
}
