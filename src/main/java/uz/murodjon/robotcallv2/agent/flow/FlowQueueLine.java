package uz.murodjon.robotcallv2.agent.flow;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.queue.HoldMusicGenerator;
import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An operator queue's waiting line for a call on an engine that has no queue dispatcher of its own
 * (LiveKit): callers wait in order with hold music and the queue's announcements, and the one at
 * the head is offered to the queue's members through {@link FlowPeople}. What {@link FlowQueueRunner}
 * does for Asterisk, less its companion agent, post-call survey and wait-time estimate.
 */
@Component
class FlowQueueLine {

    private final FlowPeople flowPeople;
    private final FlowPrompter prompter;
    private final CallEventUseCase callEventUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final Clock clock;
    private final CallFlowProperties callFlowProperties;

    /** Queue id -> the rooms waiting in it, in the order they arrived. */
    private final Map<Long, List<String>> lines = new ConcurrentHashMap<>();

    FlowQueueLine(FlowPeople flowPeople, FlowPrompter prompter, CallEventUseCase callEventUseCase,
                  CallAttemptUseCase callAttemptUseCase, Clock clock, CallFlowProperties callFlowProperties) {
        this.flowPeople = flowPeople;
        this.prompter = prompter;
        this.callEventUseCase = callEventUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.clock = clock;
        this.callFlowProperties = callFlowProperties;
    }

    /**
     * Waits the call in {@code queue} until an operator has talked to the caller and gone, the caller
     * gives up or takes the callback, or the queue overflows.
     */
    QueueOutcome waitInQueue(FlowCall call, CallQueue queue) throws InterruptedException {
        if (queue == null || !queue.enabled()) {
            call.setMissedReason(MissedCallReason.NO_OPERATOR);
            return QueueOutcome.OVERFLOW;
        }
        List<Long> memberIds = queue.members().stream()
                .sorted(Comparator.comparingInt(CallQueueMember::priority))
                .map(CallQueueMember::userId)
                .toList();
        if (!flowPeople.anySignedIn(call.companyId(), memberIds)) {
            return overflow(call, queue, "nobody signed in");
        }
        List<String> line = lines.computeIfAbsent(queue.id(), id -> new CopyOnWriteArrayList<>());
        if (queue.maxLength() > 0 && line.size() >= queue.maxLength()) {
            return overflow(call, queue, "queue full");
        }
        line.add(call.channelId());
        call.setQueueId(queue.id());
        callAttemptUseCase.markEnqueued(call.callAttemptId(), queue.id());
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.ENQUEUED,
                "queueId", queue.id(), "position", line.size());
        try {
            return wait(call, queue, line, memberIds);
        } finally {
            line.remove(call.channelId());
        }
    }

    private QueueOutcome wait(FlowCall call, CallQueue queue, List<String> line, List<Long> memberIds)
            throws InterruptedException {
        Instant start = clock.instant();
        boolean announced = false;
        boolean offered = false;
        int lastPosition = -1;
        long now = System.currentTimeMillis();
        long nextPositionAt = Long.MAX_VALUE;
        long nextPeriodicAt = queue.periodicAnnounceEverySec() > 0 ? now + queue.periodicAnnounceEverySec() * 1000L
                : Long.MAX_VALUE;
        long offerAt = queue.callbackOfferAfterSec() > 0 ? now + queue.callbackOfferAfterSec() * 1000L : Long.MAX_VALUE;
        while (true) {
            if (call.isGone()) {
                return abandoned(call, queue, start);
            }
            long waitedMs = Duration.between(start, clock.instant()).toMillis();
            if (waitedMs >= queue.maxWaitSec() * 1000L) {
                return overflow(call, queue, "waited " + waitedMs / 1000 + "s");
            }
            int position = line.indexOf(call.channelId()) + 1;
            if (position == 1) {
                QueueOutcome outcome = flowPeople.ringNextUser(call, memberIds, queue.ringTimeoutSec());
                if (outcome == QueueOutcome.ANSWERED) {
                    return QueueOutcome.ANSWERED;
                }
                if (outcome == QueueOutcome.ABANDONED) {
                    return abandoned(call, queue, start);
                }
            }
            now = System.currentTimeMillis();
            if (!announced) {
                announced = true;
                prompter.say(call, PbxPhrases.allOperatorsBusy(call.language()), false);
                lastPosition = announcePosition(call, line, lastPosition, true);
                nextPositionAt = queue.announcePositionEverySec() > 0 ? now + queue.announcePositionEverySec() * 1000L
                        : Long.MAX_VALUE;
                continue;
            }
            if (now >= nextPositionAt) {
                lastPosition = announcePosition(call, line, lastPosition, true);
                nextPositionAt = now + queue.announcePositionEverySec() * 1000L;
                continue;
            }
            if (position > 0 && position != lastPosition) {
                lastPosition = announcePosition(call, line, lastPosition, false);
                if (queue.announcePositionEverySec() > 0) {
                    nextPositionAt = now + queue.announcePositionEverySec() * 1000L;
                }
                continue;
            }
            if (now >= nextPeriodicAt) {
                prompter.say(call, queue.periodicAnnounceText(), false);
                nextPeriodicAt = now + queue.periodicAnnounceEverySec() * 1000L;
                continue;
            }
            if (!offered && now >= offerAt) {
                offered = true;
                prompter.say(call, PbxPhrases.callbackOffer(call.language()), true);
                continue;
            }
            Character key = call.digits().poll(Math.max(200, callFlowProperties.queuePollMs()), call::isGone);
            if (offered && key != null && key == '1') {
                prompter.say(call, PbxPhrases.callbackAccepted(call.language()), false);
                call.setMissedReason(MissedCallReason.CALLBACK_REQUESTED);
                callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.CALLBACK_REQUESTED,
                        "queueId", queue.id());
                return QueueOutcome.CALLBACK;
            }
            ensureHoldAudio(call, queue);
        }
    }

    private int announcePosition(FlowCall call, List<String> line, int lastPosition, boolean force) {
        int position = line.indexOf(call.channelId()) + 1;
        if (position <= 0 || !force && position == lastPosition) {
            return lastPosition;
        }
        prompter.say(call, PbxPhrases.queuePosition(position, null, call.language()), false);
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.POSITION_ANNOUNCED,
                "position", position);
        return position;
    }

    private QueueOutcome abandoned(FlowCall call, CallQueue queue, Instant start) {
        int waited = (int) Duration.between(start, clock.instant()).toSeconds();
        callAttemptUseCase.markAbandoned(call.callAttemptId(), waited);
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.ABANDONED,
                "queueId", queue.id(), "waitSeconds", waited);
        call.setMissedReason(MissedCallReason.ABANDONED);
        return QueueOutcome.ABANDONED;
    }

    private QueueOutcome overflow(FlowCall call, CallQueue queue, String reason) {
        call.setMissedReason(MissedCallReason.NO_OPERATOR);
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.OVERFLOW,
                "queueId", queue.id(), "reason", reason);
        return QueueOutcome.OVERFLOW;
    }

    private void ensureHoldAudio(FlowCall call, CallQueue queue) {
        if (call.endpoint().isPlaying()) {
            return;
        }
        short[] audio = queue.holdMusicFileId() != null
                ? prompter.recording(call.companyId(), queue.holdMusicFileId()) : new short[0];
        call.endpoint().playPcm(audio.length > 0 ? audio : HoldMusicGenerator.melody());
    }
}
