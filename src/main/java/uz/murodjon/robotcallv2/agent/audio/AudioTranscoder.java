package uz.murodjon.robotcallv2.agent.audio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Decodes whatever audio a browser hands us into the PCM the speech pipeline works in.
 *
 * <p>A recording made in the browser arrives as whatever {@code MediaRecorder} produces —
 * WebM/Opus from Chrome and Firefox, MP4/AAC from Safari — at 44.1 or 48 kHz, and nothing
 * in the JVM reads those containers. Rather than a pure-Java decoder per codec, this shells
 * out to {@code ffmpeg}, which reads all of them and resamples on the way out. The binary
 * is installed in the runtime image (Dockerfile); a local run needs it on the PATH or
 * named by {@code voice-agent.audio.ffmpeg-path}.
 */
@Component
public class AudioTranscoder {

    /** Generous for a 30-second clip; a hang here is ffmpeg waiting on a stream that never ends. */
    private static final long TIMEOUT_SECONDS = 30;
    private static final int STDERR_TAIL_CHARS = 300;

    private final String ffmpegPath;

    public AudioTranscoder(@Value("${voice-agent.audio.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    /**
     * {@code audio} — any container and codec ffmpeg can read — as 16-bit mono PCM at
     * {@code sampleRate}.
     *
     * @throws ValidationException      the bytes are not audio ffmpeg can decode
     * @throws ExternalServiceException ffmpeg is missing, or died without a verdict
     */
    public short[] decode(byte[] audio, int sampleRate) {
        Process process;
        try {
            process = new ProcessBuilder(ffmpegPath, "-hide_banner", "-loglevel", "error", "-nostdin",
                    "-i", "pipe:0", "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "1",
                    "-ar", String.valueOf(sampleRate), "pipe:1").start();
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.AUDIO_TRANSCODER_UNAVAILABLE, "ffmpeg", e, ffmpegPath);
        }

        // stdin, stdout and stderr have to move at the same time: ffmpeg blocks on a full
        // stdout pipe while we would block feeding stdin, and neither would finish.
        Thread.ofVirtual().start(() -> feed(process.getOutputStream(), audio));
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread stderrReader = Thread.ofVirtual().start(() -> drain(process.getErrorStream(), stderr));
        try {
            byte[] pcm = process.getInputStream().readAllBytes();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ExternalServiceException(ErrorCode.AUDIO_TRANSCODER_FAILED, "ffmpeg",
                        "timed out after " + TIMEOUT_SECONDS + "s");
            }
            stderrReader.join();
            if (process.exitValue() != 0) {
                throw new ValidationException(ErrorCode.AUDIO_UPLOAD_INVALID, tail(stderr));
            }
            return toSamples(pcm);
        } catch (IOException e) {
            process.destroyForcibly();
            throw new ExternalServiceException(ErrorCode.AUDIO_TRANSCODER_FAILED, "ffmpeg", e, e.getMessage());
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(ErrorCode.AUDIO_TRANSCODER_FAILED, "ffmpeg", e, "interrupted");
        }
    }

    private static void feed(OutputStream stdin, byte[] audio) {
        try (stdin) {
            stdin.write(audio);
        } catch (IOException e) {
            // ffmpeg rejected the input and closed stdin early; the exit code carries the verdict.
        }
    }

    private static void drain(InputStream stderr, ByteArrayOutputStream into) {
        try (stderr) {
            stderr.transferTo(into);
        } catch (IOException e) {
            // The process is gone; whatever was captured is what we report.
        }
    }

    private static String tail(ByteArrayOutputStream stderr) {
        String text = stderr.toString(StandardCharsets.UTF_8).trim();
        return text.length() <= STDERR_TAIL_CHARS ? text : text.substring(text.length() - STDERR_TAIL_CHARS);
    }

    private static short[] toSamples(byte[] pcm16le) {
        short[] samples = new short[pcm16le.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) ((pcm16le[i * 2] & 0xFF) | (pcm16le[i * 2 + 1] << 8));
        }
        return samples;
    }
}
