package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.DialogEngine;
import uz.murodjon.robotcallv2.agent.dialog.DialogExecutors;
import uz.murodjon.robotcallv2.agent.dialog.PiiRedactor;
import uz.murodjon.robotcallv2.agent.dialog.RealtimeDialogEngine;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.queue.CallQueueDispatcher;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Where a caller's keypad digit goes: a queue forward waiting for its confirmation, a call
 * flow's menu, or — on a call whose agent takes digits — the dialog, as the caller's turn.
 */
@Component
class DtmfInputRouter {

    private static final Logger log = LoggerFactory.getLogger(DtmfInputRouter.class);

    /**
     * How long after the last key a typed sequence counts as finished. A card number or a
     * PINFL is typed at about a key a second, with the odd pause to read the next group off
     * the card; anything shorter cuts the number into several turns. {@code #} ends it at
     * once, which is what the agent asks for when it wants a number typed.
     */
    private static final long DIGIT_GAP_MS = 2000;

    private final CallQueueDispatcher callQueueDispatcher;
    private final FlowCallRegistry flowCallRegistry;
    private final CallSessionRegistry callSessionRegistry;
    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final ExecutorService callExecutor;
    private final OperatorAcceptance operatorAcceptance;
    private final DialogExecutors dialogExecutors;

    /** Digits typed on a dialog call and not yet handed over, per channel. */
    private final Map<String, PendingDigits> pending = new ConcurrentHashMap<>();

    DtmfInputRouter(CallQueueDispatcher callQueueDispatcher,
                    FlowCallRegistry flowCallRegistry,
                    CallSessionRegistry callSessionRegistry,
                    DialogEngine dialogEngine,
                    RealtimeDialogEngine realtimeDialogEngine,
                    CallTranscriptUseCase callTranscriptUseCase,
                    ExecutorService callExecutor,
                    OperatorAcceptance operatorAcceptance,
                    DialogExecutors dialogExecutors) {
        this.callQueueDispatcher = callQueueDispatcher;
        this.flowCallRegistry = flowCallRegistry;
        this.callSessionRegistry = callSessionRegistry;
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.callExecutor = callExecutor;
        this.operatorAcceptance = operatorAcceptance;
        this.dialogExecutors = dialogExecutors;
    }

    /**
     * A keypad digit on a call whose campaign asked for them
     * ({@code campaign.dtmf_input_enabled}). Asterisk decodes RFC2833 itself, so the digit
     * arrives as a character on the event stream and never touches the RTP path.
     *
     * <p>Handled like a spoken answer: the first key silences a bot that was mid-sentence,
     * and the whole sequence becomes one caller turn once {@code #} is pressed or the keys
     * stop for {@link #DIGIT_GAP_MS}. One turn per key turned a typed PINFL into fourteen
     * model round trips, each answering a single digit. Only {@code 0-9} and {@code #} —
     * {@code *} and the A-D tones mean nothing in any scenario here, and handing one to the
     * model as an utterance would only buy a puzzled reply.
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
        // An operator leg being asked to accept a transfer without a queue.
        if (operatorAcceptance.onDigit(channelId, digit.charAt(0))) {
            return;
        }
        FlowCall flowCall = flowCallRegistry.find(channelId);
        if (flowCall != null && !flowCall.agentActive()) {
            flowCall.digits().offer(digit.charAt(0));
            return;
        }
        char key = digit.charAt(0);
        boolean terminator = key == '#';
        if (!terminator && (key < '0' || key > '9')) {
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
        if (terminator) {
            flush(channelId, session);
            return;
        }
        if (cascade) {
            // On this thread, not the executor: it only flips flags and drops queued audio,
            // and it has to land before a '#' that follows it hands the sequence over.
            CallMdc.with(channelId, () -> dialogEngine.notifyKeypress(channelId));
        }
        pending.compute(channelId, (id, current) -> {
            PendingDigits digits = current;
            if (digits == null) {
                int offsetMs = (int) (Instant.now().toEpochMilli() - session.startedAt().toEpochMilli());
                digits = new PendingDigits(offsetMs, cascade);
            }
            digits.keys.append(key);
            if (digits.flush != null) {
                digits.flush.cancel(false);
            }
            digits.flush = dialogExecutors.scheduleOnWorker(() -> flush(id, session), DIGIT_GAP_MS);
            return digits;
        });
    }

    /** Hand the digits typed so far on {@code channelId} to the dialog as one turn. */
    private void flush(String channelId, CallSession session) {
        PendingDigits digits = pending.remove(channelId);
        if (digits == null || digits.keys.isEmpty()) {
            return;
        }
        if (digits.flush != null) {
            digits.flush.cancel(false);
        }
        String typed = digits.keys.toString();
        callExecutor.execute(() -> CallMdc.with(channelId, () -> {
            // A whole typed number is now one line, so it is a card or a PINFL in the clear:
            // redacted where it is kept, while the model still gets the digits it asked for.
            String kept = PiiRedactor.redact(typed);
            log.info("[{}] DTMF: {}", channelId, kept);
            callTranscriptUseCase.addTranscript(session.callAttemptId(), "CLIENT", kept, null, digits.offsetMs, 1f);
            if (digits.cascade) {
                dialogEngine.onKeypadInput(channelId, typed);
            } else {
                // A speech-to-speech engine hears audio only: the digits are told to it in words.
                realtimeDialogEngine.onDtmf(channelId, typed);
            }
        }));
    }

    /**
     * One typed sequence in progress. Only ever touched inside {@code pending.compute} or
     * after {@code pending.remove} has taken it out, so the map is what serializes it.
     */
    private static final class PendingDigits {
        private final StringBuilder keys = new StringBuilder();
        private final int offsetMs;
        private final boolean cascade;
        private ScheduledFuture<?> flush;

        private PendingDigits(int offsetMs, boolean cascade) {
            this.offsetMs = offsetMs;
            this.cascade = cascade;
        }
    }
}
