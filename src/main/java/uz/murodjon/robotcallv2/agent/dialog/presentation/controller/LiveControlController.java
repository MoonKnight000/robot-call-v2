package uz.murodjon.robotcallv2.agent.dialog.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.agent.dialog.LiveCallControlHub;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.Map;

/**
 * REST endpoint for live call whisper injection and human operator takeover.
 */
@RestController
@RequestMapping("/api/calls/live")
public class LiveControlController {

    private final LiveCallControlHub hub;

    public LiveControlController(LiveCallControlHub hub) {
        this.hub = hub;
    }

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/whisper")
    public ResponseEntity<ResponseData<Map<String, String>>> whisper(
            @PathVariable String channelId,
            @RequestBody Map<String, String> body) {
        String instruction = body.get("instruction");
        hub.injectWhisper(channelId, instruction);
        return ResponseEntity.ok(ResponseData.ok(Map.of("channelId", channelId, "status", "WHISPER_INJECTED")));
    }

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/{channelId}/takeover")
    public ResponseEntity<ResponseData<Map<String, String>>> takeover(
            @PathVariable String channelId,
            @RequestBody(required = false) Map<String, String> body) {
        String ext = body != null ? body.get("extension") : null;
        hub.takeoverCall(channelId, ext);
        return ResponseEntity.ok(ResponseData.ok(Map.of("channelId", channelId, "status", "TAKEOVER_TRIGGERED")));
    }
}
