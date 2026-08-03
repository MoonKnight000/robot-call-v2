package uz.murodjon.uysotvoice.callrecord.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import uz.murodjon.uysotvoice.callrecord.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.callrecord.dto.HangupResponse;
import uz.murodjon.uysotvoice.callrecord.dto.LiveCallRow;
import uz.murodjon.uysotvoice.callrecord.dto.PlayResponse;
import uz.murodjon.uysotvoice.callrecord.dto.SayResponse;
import uz.murodjon.uysotvoice.callrecord.dto.TransferResponse;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
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

    /**
     * @param scenarioId test-drive this scenario without a campaign (ROADMAP A.3/A.4
     *                   "sinov rejimi"); omit to use the configured default test scenario
     */
    @PostMapping
    ResponseEntity<ResponseData<CallOriginateResponse>> call(
            @RequestParam String number, @RequestParam(required = false) Long scenarioId);

    /**
     * Test-drive an unsaved scenario draft (backend-uchun-talablar.md §4) — the body is a
     * full {@code ScenarioDefinition}, exactly as edited in the scenario form, not yet
     * saved. Validated the same way a real scenario is; nothing here is persisted.
     */
    @PostMapping("/test")
    ResponseEntity<ResponseData<CallOriginateResponse>> testCall(
            @RequestParam String number, @RequestBody ScenarioDefinition definition);

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

    /** "Tugatish" (§10.3) — force-end a live channel, with confirmation on the client side. */
    @PostMapping("/{channelId}/hangup")
    ResponseEntity<ResponseData<HangupResponse>> hangup(@PathVariable String channelId);

    /** "Operatorga uzatish" (§10.3, §11.6) — bridge a live channel to a human operator. */
    @PostMapping("/{channelId}/transfer")
    ResponseEntity<ResponseData<TransferResponse>> transfer(@PathVariable String channelId);

    /**
     * "Tinglash" (§10.3) — join a live channel as a listener: a continuous {@code
     * audio/wav} stream of the caller's and bot's mixed audio, for an {@code <audio>}
     * element to play. Not wrapped in {@code ResponseData} (raw audio body, like a
     * file download). Ends when the client disconnects or the call itself does.
     */
    @GetMapping(value = "/{channelId}/listen", produces = "audio/wav")
    ResponseEntity<StreamingResponseBody> listen(@PathVariable String channelId);
}
