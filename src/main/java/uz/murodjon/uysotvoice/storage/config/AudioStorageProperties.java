package uz.murodjon.uysotvoice.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage and retention settings, shared by every {@code stored_file} use
 * (recordings, images, documents). Bound from {@code voice-agent.storage.*}
 * (PROJECT.md §2.3, §11.3, Stage 9).
 *
 * @param enabled                off by default; when off, uploads fail/degrade — see
 *                               {@code FileStorageService}
 * @param endpoint               MinIO/S3 endpoint URL
 * @param accessKey              access key
 * @param secretKey              secret key
 * @param bucket                 the one shared bucket every {@code stored_file} lives
 *                               in (created if missing) — object keys are namespaced
 *                               per company/category, so one bucket is enough
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
