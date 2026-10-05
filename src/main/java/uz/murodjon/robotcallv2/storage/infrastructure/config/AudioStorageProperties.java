package uz.murodjon.robotcallv2.storage.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage settings, shared by every {@code stored_file} use — call recordings,
 * company logos, user avatars. Bound from {@code voice-agent.storage.*}. How long a
 * recording is kept is {@link RetentionProperties}' business.
 *
 * @param enabled                 when false nothing is uploaded, and a recording stays a
 *                                local file the UI cannot play back
 * @param deleteLocalAfterUpload  whether the local copy is removed once MinIO has it
 */
@ConfigurationProperties(prefix = "voice-agent.storage")
public record AudioStorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        boolean deleteLocalAfterUpload
) {
    public AudioStorageProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "files";
        }
    }
}
