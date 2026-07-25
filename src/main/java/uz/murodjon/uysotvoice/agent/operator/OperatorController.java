package uz.murodjon.uysotvoice.agent.operator;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.uysotvoice.agent.dialog.DialogEngine;
import uz.murodjon.uysotvoice.agent.dialog.OperatorSnapshot;

/**
 * Read-only API for the operator screen (PROJECT.md §11.6, Stage 11). After a
 * transfer the operator UI polls this for the caller's facts, current dialog state,
 * and the conversation so far. Served live from the still-open dialog session.
 */
@RestController
@RequestMapping("/api/operator")
public class OperatorController {

    private final DialogEngine dialogEngine;

    public OperatorController(DialogEngine dialogEngine) {
        this.dialogEngine = dialogEngine;
    }

    @GetMapping("/calls/{channelId}")
    public ResponseEntity<OperatorSnapshot> context(@PathVariable String channelId) {
        OperatorSnapshot snapshot = dialogEngine.operatorSnapshot(channelId);
        return snapshot != null ? ResponseEntity.ok(snapshot) : ResponseEntity.notFound().build();
    }
}
