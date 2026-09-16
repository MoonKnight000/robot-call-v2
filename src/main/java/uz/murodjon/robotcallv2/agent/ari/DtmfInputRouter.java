package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.DialogEngine;
import uz.murodjon.robotcallv2.agent.dialog.RealtimeDialogEngine;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.queue.CallQueueDispatcher;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;

import java.time.Instant;
import java.util.concurrent.ExecutorService;

/**
 * Where a caller's keypad digit goes: a queue forward waiting for its confirmation, a call
 * flow's menu, or — on a call whose agent takes digits — the dialog, as the caller's turn.
 */
@Component
class DtmfInputRouter {

    private static final Logger log = LoggerFactory.getLogger(DtmfInputRouter.class);

    private final CallQueueDispatcher callQueueDispatcher;
    private final FlowCallRegistry flowCallRegistry;
    private final CallSessionRegistry callSessionRegistry;
    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final ExecutorService callExecutor;

    DtmfInputRouter(CallQueueDispatcher callQueueDispatcher,
                    FlowCallRegistry flowCallRegistry,
                    CallSessionRegistry callSessionRegistry,
                    DialogEngine dialogEngine,
                    RealtimeDialogEngine realtimeDialogEngine,
                    CallTranscriptUseCase callTranscriptUseCase,
                    ExecutorService callExecutor) {
        this.callQueueDispatcher = callQueueDispatcher;
        this.flowCallRegistry = flowCallRegistry;
        this.callSessionRegistry = callSessionRegistry;
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.callExecutor = callExecutor;
    }

    /**
     * A keypad digit on a call whose campaign asked for them
     * ({@code campaign.dtmf_input_enabled}). Asterisk decodes RFC2833 itself, so the digit
     * arrives as a character on the event stream and never touches the RTP path.
     *
     * <p>Handled exactly like a spoken answer: it silences a bot that was mid-sentence and
     * becomes the caller's turn. Only {@code 0-9} — {@code *}, {@code #} and the A-D tones
     * mean nothing in any scenario here, and handing one to the model as an utterance
     * would only buy a puzzled reply.
     */
    void onDtmf(ChannelDtmfReceived event) {
        Channel channel = event.getChannel();
        String digit = event.getDigit();
        if (channel == null || digit == null || digit.length() != 1) {
            return;
        }
        String channelId = channel.getId();
        // A forwarded leg confirming with 1, then a menu or a queue — only then a dialog turn.
        if (callQueueDispatcher.onDigit(channelId, digit.charAt(0))) {
            return;
        }
        FlowCall flowCall = flowCallRegistry.find(channelId);
        if (flowCall != null && !flowCall.agentActive()) {
            flowCall.digits().offer(digit.charAt(0));
            return;
        }
        if (digit.charAt(0) < '0' || digit.charAt(0) > '9') {
            return;
        }
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null || !session.dtmfInputEnabled()) {
            return;
        }
        boolean cascade = dialogEngine.owns(channelId);
        if (!cascade && !realtimeDialogEngine.owns(channelId)) {
            log.warn("[{}] DTMF '{}' ignored — no dialog engine owns this call", channelId, digit);
            return;
        }
        callExecutor.execute(() -> CallMdc.with(channelId, () -> {
            log.info("[{}] DTMF: {}", channelId, digit);
            int offsetMs = (int) (Instant.now().toEpochMilli() - session.startedAt().toEpochMilli());
            callTranscriptUseCase.addTranscript(session.callAttemptId(), "CLIENT", digit, null, offsetMs, 1f);
            if (cascade) {
                dialogEngine.notifyBargeIn(channelId);
                dialogEngine.onClientFinal(channelId, digit);
            } else {
                // A speech-to-speech engine hears audio only: the digit is told to it in words.
                realtimeDialogEngine.onDtmf(channelId, digit);
            }
        }));
    }
}
