package uz.murodjon.robotcallv2.dialer.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.dialer.application.service.InstantCallTriggerService;
import uz.murodjon.robotcallv2.dialer.presentation.dto.InstantCallRequest;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.Map;

/**
 * Controller for triggering instant high-priority outbound calls (PROJECT.md §5.2).
 */
@RestController
@RequestMapping("/api/calls/instant")
public class TriggerCallController {

    private final InstantCallTriggerService triggerService;
    private final CurrentCompany currentCompany;

    public TriggerCallController(InstantCallTriggerService triggerService, CurrentCompany currentCompany) {
        this.triggerService = triggerService;
        this.currentCompany = currentCompany;
    }

    @PostMapping
    public ResponseEntity<ResponseData<Map<String, Object>>> trigger(@RequestBody InstantCallRequest request) {
        long targetId = triggerService.triggerCall(
                currentCompany.id(),
                request.campaignId(),
                request.phone(),
                request.clientName(),
                request.contextData()
        );
        return ResponseEntity.ok(ResponseData.ok(Map.of("targetId", targetId, "status", "QUEUED_INSTANT")));
    }
}
