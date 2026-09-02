package uz.murodjon.robotcallv2.operator.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.live.application.service.LiveBroadcastService;
import uz.murodjon.robotcallv2.live.domain.entity.LiveNotification;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorUseCase;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Service for the operator screen: read context, live takeover, and supervisor whisper (PROJECT.md §11.6).
 */
@Service
public class OperatorService implements OperatorUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);

    private final DialogRouter dialogRouter;
    private final LiveBroadcastService broadcast;

    public OperatorService(DialogRouter dialogRouter, LiveBroadcastService broadcast) {
        this.dialogRouter = dialogRouter;
        this.broadcast = broadcast;
    }

    @Override
    public OperatorSnapshot snapshot(String channelId) {
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(channelId);
        if (snapshot == null) {
            throw new NotFoundException(ErrorCode.OPERATOR_SNAPSHOT_NOT_FOUND, channelId);
        }
        return snapshot;
    }

    @Override
    public void takeover(String channelId, String operatorExtension) {
        log.info("[{}] Supervisor takeover initiated by operator extension {}", channelId, operatorExtension);
        broadcast.publish(LiveEventType.NOTIFICATION,
                new LiveNotification("TAKEOVER", "Operator " + operatorExtension + " qo'ng'iroqni qabul qildi", channelId));
        dialogRouter.endCall(channelId);
    }

    @Override
    public void whisper(String channelId, String message) {
        log.info("[{}] Supervisor whisper sent: {}", channelId, message);
        broadcast.publish(LiveEventType.NOTIFICATION,
                new LiveNotification("WHISPER", "Supervisor: " + message, channelId));
    }
}
