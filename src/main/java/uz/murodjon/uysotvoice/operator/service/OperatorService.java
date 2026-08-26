package uz.murodjon.uysotvoice.operator.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.DialogRouter;
import uz.murodjon.uysotvoice.agent.dialog.OperatorSnapshot;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;

/**
 * Read-only lookup for the operator screen (PROJECT.md §11.6, Stage 11). After a
 * transfer the operator UI polls this for the caller's facts, current dialog state,
 * and the conversation so far. Served live from the still-open dialog session, whichever
 * engine is running it ({@link DialogRouter}).
 */
@Service
public class OperatorService {

    private final DialogRouter dialogRouter;

    public OperatorService(DialogRouter dialogRouter) {
        this.dialogRouter = dialogRouter;
    }

    public OperatorSnapshot snapshot(String channelId) {
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(channelId);
        if (snapshot == null) {
            throw new NotFoundException(ErrorCode.OPERATOR_SNAPSHOT_NOT_FOUND, channelId);
        }
        return snapshot;
    }
}
