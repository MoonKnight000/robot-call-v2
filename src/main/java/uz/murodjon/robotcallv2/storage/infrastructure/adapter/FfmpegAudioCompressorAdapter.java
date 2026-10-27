package uz.murodjon.robotcallv2.storage.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.storage.application.port.output.AudioCompressorPort;
import uz.murodjon.robotcallv2.storage.domain.entity.CompressedAudio;
import uz.murodjon.robotcallv2.storage.infrastructure.config.AudioStorageProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Compresses call recordings from WAV to mono MP3 using ffmpeg prior to storage upload.
 * If compression is disabled, ffmpeg is missing, or conversion fails, it gracefully falls
 * back to the original uncompressed WAV file.
 */
@Component
public class FfmpegAudioCompressorAdapter implements AudioCompressorPort {

    private static final Logger log = LoggerFactory.getLogger(FfmpegAudioCompressorAdapter.class);
    private static final long TIMEOUT_SECONDS = 30;
    private static final String MP3_CONTENT_TYPE = "audio/mpeg";

    private final AudioStorageProperties properties;

    public FfmpegAudioCompressorAdapter(AudioStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public CompressedAudio compress(Path sourceWav, String originalName) {
        String safeOriginalName = originalName != null && !originalName.isBlank() ? originalName : "call.wav";
        if (!properties.compressAudio() || sourceWav == null || !Files.exists(sourceWav)) {
            return CompressedAudio.uncompressed(sourceWav, safeOriginalName);
        }

        try {
            long sourceSize = Files.size(sourceWav);
            if (sourceSize <= 44) {
                return CompressedAudio.uncompressed(sourceWav, safeOriginalName);
            }

            Path tempMp3 = Files.createTempFile("rec-compressed-", ".mp3");
            try {
                Process process = new ProcessBuilder(
                        properties.ffmpegPath(),
                        "-y",
                        "-hide_banner",
                        "-loglevel", "error",
                        "-nostdin",
                        "-i", sourceWav.toAbsolutePath().toString(),
                        "-vn",
                        "-ac", "1",
                        "-b:a", properties.bitrateKbps() + "k",
                        tempMp3.toAbsolutePath().toString()
                ).start();

                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    log.warn("ffmpeg compression timed out after {}s for {}; using original WAV",
                            TIMEOUT_SECONDS, sourceWav);
                    Files.deleteIfExists(tempMp3);
                    return CompressedAudio.uncompressed(sourceWav, safeOriginalName);
                }

                if (process.exitValue() != 0 || Files.size(tempMp3) == 0) {
                    log.warn("ffmpeg compression failed with exit code {}; using original WAV", process.exitValue());
                    Files.deleteIfExists(tempMp3);
                    return CompressedAudio.uncompressed(sourceWav, safeOriginalName);
                }

                long compressedSize = Files.size(tempMp3);
                log.debug("Compressed audio recording: {} bytes -> {} bytes ({})",
                        sourceSize, compressedSize, tempMp3);

                String targetName = toMp3Filename(safeOriginalName);
                return new CompressedAudio(tempMp3, targetName, MP3_CONTENT_TYPE, true);
            } catch (Exception e) {
                Files.deleteIfExists(tempMp3);
                throw e;
            }
        } catch (IOException e) {
            log.info("ffmpeg not available or failed ({}); uploading uncompressed WAV", e.getMessage());
            return CompressedAudio.uncompressed(sourceWav, safeOriginalName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Audio compression interrupted; uploading uncompressed WAV");
            return CompressedAudio.uncompressed(sourceWav, safeOriginalName);
        }
    }

    private static String toMp3Filename(String originalName) {
        if (originalName.toLowerCase().endsWith(".wav")) {
            return originalName.substring(0, originalName.length() - 4) + ".mp3";
        }
        return originalName + ".mp3";
    }
}
