package uz.murodjon.uysotvoice.agent.ari;

import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/**
 * Manual triggers for verification.
 * <ul>
 *   <li>{@code POST /api/calls?number=...} — originate a call (Stage 2).</li>
 *   <li>{@code POST /api/calls/{channelId}/play?file=...} — play a WAV to the
 *       caller over RTP (Stage 4).</li>
 *   <li>{@code POST /api/calls/{channelId}/say?text=...&language=uz-UZ} —
 *       synthesize text and speak it to the caller (Stage 6).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final AriService ariService;

    public CallController(AriService ariService) {
        this.ariService = ariService;
    }

    @PostMapping
    public Map<String, String> call(@RequestParam String number) {
        String channelId = ariService.originate(number);
        return Map.of("number", number, "channelId", channelId);
    }

    @PostMapping("/{channelId}/play")
    public Map<String, String> play(@PathVariable String channelId, @RequestParam String file) {
        ariService.play(channelId, Path.of(file));
        return Map.of("channelId", channelId, "file", file, "status", "playing");
    }

    @PostMapping("/{channelId}/say")
    public Map<String, String> say(@PathVariable String channelId,
                                   @RequestParam String text,
                                   @RequestParam(required = false) String language) {
        ariService.speak(channelId, text, language);
        return Map.of("channelId", channelId, "status", "speaking");
    }
}
