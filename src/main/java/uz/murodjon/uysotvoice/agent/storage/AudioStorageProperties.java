package uz.murodjon.uysotvoice.agent.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage and retention settings for call recordings. Bound from
 * {@code voice-agent.storage.*} (PROJECT.md §2.3, §11.3, Stage 9).
 *
 * @param enabled                off by default; when off, recordings stay local and no URL is set
 * @param endpoint               MinIO/S3 endpoint URL
 * @param accessKey              access key
 * @param secretKey              secret key
 * @param bucket                 target bucket (created if missing)
 * @param deleteLocalAfterUpload remove the on-disk WAV once it is safely in object
 *                               storage. Without this the recording directory grows
 *                               for the life of the deployment — every call leaves a
 *                               second copy behind
 * @param retentionDays          how long a recording is kept (§11.3 requires the period
 *                               to be configurable). {@code 0} keeps everything forever
 */
@ConfigurationProperties(prefix = "voice-agent.storage")
public record AudioStorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        boolean deleteLocalAfterUpload,
        int retentionDays
) {
}
