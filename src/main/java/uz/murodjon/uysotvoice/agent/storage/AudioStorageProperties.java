package uz.murodjon.uysotvoice.agent.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage settings for call recordings. Bound from
 * {@code voice-agent.storage.*} (PROJECT.md §2.3, Stage 9).
 *
 * @param enabled   off by default; when off, recordings stay local and no URL is set
 * @param endpoint  MinIO/S3 endpoint URL
 * @param accessKey access key
 * @param secretKey secret key
 * @param bucket    target bucket (created if missing)
 */
@ConfigurationProperties(prefix = "voice-agent.storage")
public record AudioStorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket
) {
}
