package uz.murodjon.uysotvoice.operator.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.DialogEngine;
import uz.murodjon.uysotvoice.agent.dialog.OperatorSnapshot;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;

/**
 * Read-only lookup for the operator screen (PROJECT.md §11.6, Stage 11). After a
 * transfer the operator UI polls this for the caller's facts, current dialog state,
 * and the conversation so far. Served live from the still-open dialog session.
 */
@Service
public class OperatorService {

    private final DialogEngine dialogEngine;

    public OperatorService(DialogEngine dialogEngine) {
        this.dialogEngine = dialogEngine;
    }

    public OperatorSnapshot snapshot(String channelId) {
        OperatorSnapshot snapshot = dialogEngine.operatorSnapshot(channelId);
        if (snapshot == null) {
            throw new NotFoundException("No operator snapshot for channel " + channelId);
        }
        return snapshot;
    }
}
