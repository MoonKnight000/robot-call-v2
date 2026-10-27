package uz.murodjon.robotcallv2.storage.domain.entity;

import java.nio.file.Path;

/**
 * Result of an audio compression attempt.
 *
 * @param path         the path to the audio file to upload (compressed or original)
 * @param originalName the filename to catalog in storage (e.g. call.mp3 or call.wav)
 * @param contentType  the MIME content type (e.g. audio/mpeg or audio/wav)
 * @param isTemporary  whether the file is a temporary compressed file that should be deleted after upload
 */
public record CompressedAudio(
        Path path,
        String originalName,
        String contentType,
        boolean isTemporary
) {
    public static CompressedAudio uncompressed(Path path, String originalName) {
        return new CompressedAudio(path, originalName, "audio/wav", false);
    }
}
