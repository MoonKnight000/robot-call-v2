package uz.murodjon.robotcallv2.storage.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage and retention settings, shared by every stored_file use.
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
    public AudioStorageProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "voice-recordings";
        }
    }
}
