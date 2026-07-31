package uz.murodjon.uysotvoice.call.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.uysotvoice.call.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.call.dto.LiveCallRow;
import uz.murodjon.uysotvoice.call.dto.PlayResponse;
import uz.murodjon.uysotvoice.call.dto.SayResponse;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

import java.util.List;

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
@RequestMapping("/api/calls")
public interface CallController {

    /**
     * Every call still in conversation (§10.2/§10.3 UI-DESIGN.md "Jonli qo'ng'iroqlar").
     */
    @GetMapping("/live")
    ResponseEntity<ResponseData<List<LiveCallRow>>> live();

    @PostMapping
    ResponseEntity<ResponseData<CallOriginateResponse>> call(@RequestParam String number);

    @PostMapping("/{channelId}/play")
    ResponseEntity<ResponseData<PlayResponse>> play(@PathVariable String channelId, @RequestParam String file);

    /**
     * {@code voice} is a catalog id from {@code GET /api/tts/voices} — audition it here
     * before creating a campaign with it (§2.5).
     */
    @PostMapping("/{channelId}/say")
    ResponseEntity<ResponseData<SayResponse>> say(
            @PathVariable String channelId,
            @RequestParam String text,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) String voice
    );
}
