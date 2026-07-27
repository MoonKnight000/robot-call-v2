package uz.murodjon.uysotvoice.agent.ari;

import org.springframework.web.bind.annotation.*;
import uz.murodjon.uysotvoice.agent.rtp.RtpProperties;
import uz.murodjon.uysotvoice.audit.AuditService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Manual triggers for verification.
 * <ul>
 *   <li>{@code POST /api/calls?number=...} — originate a call (Stage 2).</li>
 *   <li>{@code POST /api/calls/{channelId}/play?file=...} — play a WAV to the
 *       caller over RTP (Stage 4).</li>
 *   <li>{@code POST /api/calls/{channelId}/say?text=...&language=uz-UZ&voice=nigora} —
 *       synthesize text and speak it to the caller (Stage 6).</li>
 * </ul>
 *
 * <p>Every endpoint here spends real money (trunk minutes, TTS characters) and is
 * gated by {@code X-Api-Key} — see {@code SecurityConfig}. Inputs that reach a dial
 * string or the filesystem are validated before use.
 */
@RestController
@RequestMapping("/api/calls")
public class CallController {

    /** Enough for any realistic prompt; stops one request from running up a TTS bill. */
    private static final int MAX_SAY_CHARS = 1000;

    private final AriService ariService;
    private final RtpProperties rtpProps;
    private final AuditService audit;

    public CallController(AriService ariService, RtpProperties rtpProps, AuditService audit) {
        this.ariService = ariService;
        this.rtpProps = rtpProps;
        this.audit = audit;
    }

    @PostMapping
    public Map<String, String> call(@RequestParam String number) {
        String channelId = ariService.originate(number);
        // A manual call to a real subscriber is exactly the action someone will later need
        // to account for (§11).
        audit.record("CALL_ORIGINATE_MANUAL", "call", channelId, number);
        return Map.of("number", number, "channelId", channelId);
    }

    @PostMapping("/{channelId}/play")
    public Map<String, String> play(@PathVariable String channelId, @RequestParam String file) {
        Path resolved = resolveInRecordingDir(file);
        ariService.play(channelId, resolved);
        return Map.of("channelId", channelId, "file", resolved.toString(), "status", "playing");
    }

    /** {@code voice} is a catalog id from {@code GET /api/tts/voices} — audition it here
     * before creating a campaign with it (§2.5). */
    @PostMapping("/{channelId}/say")
    public Map<String, String> say(@PathVariable String channelId,
                                   @RequestParam String text,
                                   @RequestParam(required = false) String language,
                                   @RequestParam(required = false) String voice) {
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (text.length() > MAX_SAY_CHARS) {
            throw new IllegalArgumentException("text is longer than " + MAX_SAY_CHARS + " characters");
        }
        ariService.speak(channelId, text, language, voice);
        return Map.of("channelId", channelId, "status", "speaking");
    }

    /**
     * Resolve {@code file} inside the recording directory. Without this an API caller
     * could hand any absolute path (or one containing {@code ../}) to the WAV reader
     * and have the server read it aloud down the phone.
     */
    private Path resolveInRecordingDir(String file) {
        Path base = Path.of(rtpProps.recordingDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(file).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("file must be inside " + base);
        }
        try {
            // Resolves symlinks too — a link inside the directory must not escape it.
            Path real = resolved.toRealPath();
            if (!real.startsWith(base.toRealPath())) {
                throw new IllegalArgumentException("file must be inside " + base);
            }
            return real;
        } catch (IOException e) {
            throw new IllegalArgumentException("No such playback file: " + file);
        }
    }
}
