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
 * @param compressAudio           whether to compress call recordings (WAV -> MP3) before upload
 * @param ffmpegPath              path to the ffmpeg executable
 * @param bitrateKbps             target bitrate in kbps for compressed audio (e.g. 32)
 */
@ConfigurationProperties(prefix = "voice-agent.storage")
public record AudioStorageProperties(
        boolean enabled,
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        boolean deleteLocalAfterUpload,
        boolean compressAudio,
        String ffmpegPath,
        int bitrateKbps
) {
    public AudioStorageProperties {
        if (bucket == null || bucket.isBlank()) {
            bucket = "files";
        }
        if (ffmpegPath == null || ffmpegPath.isBlank()) {
            ffmpegPath = "ffmpeg";
        }
        if (bitrateKbps <= 0) {
            bitrateKbps = 32;
        }
    }

    public AudioStorageProperties(boolean enabled, String endpoint, String accessKey, String secretKey,
                                  String bucket, boolean deleteLocalAfterUpload) {
        this(enabled, endpoint, accessKey, secretKey, bucket, deleteLocalAfterUpload, true, "ffmpeg", 32);
    }
}
