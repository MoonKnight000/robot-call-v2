package uz.murodjon.robotcallv2.operator.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.Map;

/**
 * REST API for the operator screen (PROJECT.md §11.6, Stage 11).
 */
@RequestMapping("/api/operator")
public interface OperatorController {

    @PreAuthorize("hasAuthority('OPERATOR_READ')")
    @GetMapping("/calls/{channelId}")
    ResponseEntity<ResponseData<OperatorSnapshot>> context(@PathVariable String channelId);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/calls/{channelId}/takeover")
    ResponseEntity<ResponseData<Map<String, Object>>> takeover(
            @PathVariable String channelId,
            @RequestParam(required = false, defaultValue = "100") String extension);

    @PreAuthorize("hasAuthority('OPERATOR_EDIT')")
    @PostMapping("/calls/{channelId}/whisper")
    ResponseEntity<ResponseData<Map<String, Object>>> whisper(
            @PathVariable String channelId,
            @RequestBody Map<String, String> body);
}
