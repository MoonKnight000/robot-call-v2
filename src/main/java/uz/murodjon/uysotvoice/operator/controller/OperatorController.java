package uz.murodjon.uysotvoice.operator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import uz.murodjon.uysotvoice.agent.dialog.OperatorSnapshot;
import uz.murodjon.uysotvoice.shared.api.ResponseData;

/**
 * Read-only API for the operator screen (PROJECT.md §11.6, Stage 11). After a
 * transfer the operator UI polls this for the caller's facts, current dialog state,
 * and the conversation so far. Served live from the still-open dialog session.
 */
@RequestMapping("/api/operator")
public interface OperatorController {

    @GetMapping("/calls/{channelId}")
    ResponseEntity<ResponseData<OperatorSnapshot>> context(@PathVariable String channelId);
}
