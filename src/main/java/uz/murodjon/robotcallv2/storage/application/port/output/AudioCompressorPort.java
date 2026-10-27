package uz.murodjon.robotcallv2.storage.application.port.output;

import uz.murodjon.robotcallv2.storage.domain.entity.CompressedAudio;

import java.nio.file.Path;

/**
 * Compresses audio recordings (e.g. WAV to MP3/OGG) prior to object storage upload.
 */
public interface AudioCompressorPort {

    /**
     * Attempts to compress the given audio file to save storage and CDN bandwidth.
     * Returns a {@link CompressedAudio} record. If compression is disabled, unsupported,
     * or fails, it falls back to the original uncompressed audio file.
     *
     * @param sourceWav    the local WAV file path
     * @param originalName the suggested file name (e.g. "call_123.wav")
     * @return the compressed or fallback audio details
     */
    CompressedAudio compress(Path sourceWav, String originalName);
}
