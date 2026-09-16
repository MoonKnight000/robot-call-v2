package uz.murodjon.robotcallv2.operator.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.LiveCallControlHub;
import uz.murodjon.robotcallv2.agent.dialog.OperatorSnapshot;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorContextResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorTakeoverResponse;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperRequest;
import uz.murodjon.robotcallv2.operator.application.dto.OperatorWhisperResponse;
import uz.murodjon.robotcallv2.operator.application.port.input.OperatorUseCase;
import uz.murodjon.robotcallv2.operator.domain.event.OperatorNotificationEvent;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

/**
 * Service for the operator screen: read context, live takeover, and supervisor whisper
 * (PROJECT.md §11.6). Every action is scoped to the caller's company — a channel of another
 * tenant is answered as if there were no such call. What a supervisor did is announced as an
 * {@link OperatorNotificationEvent}, which the live stream shows.
 */
@Service
public class OperatorService implements OperatorUseCase {

    private static final Logger log = LoggerFactory.getLogger(OperatorService.class);

    private final DialogRouter dialogRouter;
    private final ApplicationEventPublisher eventPublisher;
    private final AriService ari;
    private final LiveCallControlHub liveCallControlHub;

    public OperatorService(DialogRouter dialogRouter, ApplicationEventPublisher eventPublisher, AriService ari,
                           LiveCallControlHub liveCallControlHub) {
        this.dialogRouter = dialogRouter;
        this.eventPublisher = eventPublisher;
        this.ari = ari;
        this.liveCallControlHub = liveCallControlHub;
    }

    @Override
    public OperatorContextResponse findContext(long companyId, String channelId) {
        requireOwned(companyId, channelId);
        OperatorSnapshot snapshot = dialogRouter.operatorSnapshot(channelId);
        if (snapshot == null) {
            throw new NotFoundException(ErrorCode.OPERATOR_SNAPSHOT_NOT_FOUND, channelId);
        }
        return OperatorContextResponse.of(snapshot);
    }

    @Override
    public OperatorTakeoverResponse takeover(long companyId, String channelId, String operatorExtension) {
        requireOwned(companyId, channelId);
        log.info("[{}] Supervisor takeover initiated by operator extension {}", channelId, operatorExtension);
        eventPublisher.publishEvent(new OperatorNotificationEvent(companyId, "TAKEOVER",
                "Operator " + operatorExtension + " qo'ng'iroqni qabul qildi", channelId));
        dialogRouter.endCall(channelId);
        return new OperatorTakeoverResponse(channelId, "TRANSFERRED", operatorExtension);
    }

    @Override
    public OperatorWhisperResponse whisper(long companyId, String channelId, OperatorWhisperRequest request) {
        String message = request.message();
        requireOwned(companyId, channelId);
        // The bot takes it while one is still on the call, cascade or realtime; a call
        // already with an operator gets the notification alone.
        boolean delivered = liveCallControlHub.offerWhisper(channelId, message);
        log.info("[{}] Supervisor whisper sent ({} chars, bot {})", channelId, message.length(),
                delivered ? "steered" : "not running");
        eventPublisher.publishEvent(new OperatorNotificationEvent(companyId, "WHISPER",
                "Supervisor: " + message, channelId));
        return new OperatorWhisperResponse(channelId, delivered ? "DELIVERED" : "NOTIFIED", message);
    }

    private void requireOwned(long companyId, String channelId) {
        ari.requireLiveCallOf(companyId, channelId);
    }
}
