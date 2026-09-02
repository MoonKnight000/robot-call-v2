package uz.murodjon.robotcallv2.storage.infrastructure.adapter;

import io.minio.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.storage.application.port.output.ObjectStoragePort;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MinIO/S3 object storage adapter implementation of {@link ObjectStoragePort}.
 */
@Component
public class MinioObjectStorageAdapter implements ObjectStoragePort {

    private static final Logger log = LoggerFactory.getLogger(MinioObjectStorageAdapter.class);

    private final AudioStorageProperties props;
    private volatile MinioClient client;
    private final Set<String> knownBuckets = ConcurrentHashMap.newKeySet();

    public MinioObjectStorageAdapter(AudioStorageProperties props) {
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

    @Override
    public boolean available() {
        return client != null;
    }

    @Override
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

    @Override
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

    @Override
    public InputStream download(String bucket, String objectName) throws Exception {
        MinioClient current = client;
        if (current == null) {
            throw new IOException("object storage unavailable");
        }
        return current.getObject(GetObjectArgs.builder().bucket(bucket).object(objectName).build());
    }

    @Override
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
