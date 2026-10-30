package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.audio.RingbackToneGenerator;
import uz.murodjon.robotcallv2.agent.queue.*;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowExit;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTransferTarget;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.company.domain.enums.Language;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gets a flow call to a person (docs/INBOUND-PBX-PLAN.md §4): waits it in an operator queue
 * — hold music, "sizning navbatingiz N-chi", the callback offer, an AI companion, the survey
 * after the operator — rings users or an outside number, applies a queue's overflow, and
 * hands the call to an AI agent. Used by {@link CallFlowRunner}'s nodes and for calls that
 * reach a queue outside any flow alike.
 */
@Component
public class FlowQueueRunner {

    private static final Logger log = LoggerFactory.getLogger(FlowQueueRunner.class);
    /** How long a user or an outside number rings when the flow does not say. */
    static final int DEFAULT_RING_SEC = 25;
    private static final long SURVEY_ANSWER_MS = 8000;
    private static final int MAX_OVERFLOW_DEPTH = 3;
    private static final int LOW_SURVEY_SCORE = 2;

    private final FlowPrompter prompter;
    private final VoicemailRecorder voicemailRecorder;
    private final CallQueueDispatcher dispatcher;
    private final CallQueueUseCase callQueueUseCase;
    private final CallEventUseCase callEventUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final NotificationUseCase notificationUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final ObjectProvider<AriService> ariProvider;
    private final Clock clock;
    private final CallFlowProperties callFlowProperties;
    private volatile short[] ringback;

    public FlowQueueRunner(FlowPrompter prompter, VoicemailRecorder voicemailRecorder, CallQueueDispatcher dispatcher,
                           CallQueueUseCase callQueueUseCase, CallEventUseCase callEventUseCase,
                           CallAttemptUseCase callAttemptUseCase, NotificationUseCase notificationUseCase,
                           CampaignTargetUseCase campaignTargetUseCase, ObjectProvider<AriService> ariProvider, Clock clock, CallFlowProperties callFlowProperties) {
        this.prompter = prompter;
        this.voicemailRecorder = voicemailRecorder;
        this.dispatcher = dispatcher;
        this.callQueueUseCase = callQueueUseCase;
        this.callEventUseCase = callEventUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.notificationUseCase = notificationUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.ariProvider = ariProvider;
        this.clock = clock;
        this.callFlowProperties = callFlowProperties;
    }

    /** Waits the call in queue {@code queueId}, then settles it: the queue's own overflow, or the callback asked for. */
    void waitAndSettle(FlowCall call, long queueId) throws InterruptedException {
        CallQueue queue = callQueueUseCase.findQueue(call.companyId(), queueId);
        QueueOutcome outcome = waitInQueue(call, queue);
        if (outcome == QueueOutcome.OVERFLOW) {
            applyOverflow(call, queue);
        } else if (outcome == QueueOutcome.CALLBACK) {
            queueCallback(call, queue);
        }
    }

    /** Waits the call in {@code queue}; see {@link #enqueueAndWait}. */
    QueueOutcome waitInQueue(FlowCall call, CallQueue queue) throws InterruptedException {
        return enqueueAndWait(call, queue, null, false);
    }

    QueueOutcome ringUsers(FlowCall call, List<Long> userIds, int ringSeconds, CallQueueStrategy strategy)
            throws InterruptedException {
        List<CallQueueMember> members = userIds.stream().map(id -> new CallQueueMember(id, 0, Set.of())).toList();
        return enqueueAndWait(call, adHocQueue(call, strategy, ringSeconds, members), null, false);
    }

    QueueOutcome forward(FlowCall call, String number, int ringSeconds, boolean confirm)
            throws InterruptedException {
        return enqueueAndWait(call, adHocQueue(call, CallQueueStrategy.RING_ALL, ringSeconds, List.of()), number, confirm);
    }

    /** A queue the flow gave no overflow exit: its own overflow setting decides. Always ends the call. */
    String applyOverflow(FlowCall call, CallQueue queue) throws InterruptedException {
        return applyOverflowAtDepth(call, queue, 0);
    }

    /**
     * A caller who asked to be called back: onto the queue's callback campaign when it has
     * one (the AI calls back), otherwise the missed-call list keeps them for the operators.
     */
    void queueCallback(FlowCall call, CallQueue queue) {
        if (queue == null || queue.callbackCampaignId() == null || call.callerNumber() == null) {
            return;
        }
        try {
            long targetId = campaignTargetUseCase.addTarget(call.companyId(), queue.callbackCampaignId(), 0L,
                    call.callerNumber(), call.language(), null);
            campaignTargetUseCase.scheduleCallback(call.companyId(), targetId, clock.instant().plusSeconds(60));
            call.setMissedReason(null);
        } catch (Exception e) {
            log.warn("[{}] callback campaign {} refused the caller: {}", call.channelId(), queue.callbackCampaignId(),
                    e.getMessage());
        }
    }

    /** Hands the call to an AI agent until it finishes or asks for a person. */
    CallFlowExit runAgent(FlowCall call, long agentId) throws InterruptedException {
        CompletableFuture<CallFlowExit> result = new CompletableFuture<>();
        AriService ari = ariProvider.getObject();
        boolean started = ari.startAgentOnFlowCall(call, agentId,
                () -> result.complete(CallFlowExit.COMPLETED), () -> result.complete(CallFlowExit.TRANSFER));
        if (!started) {
            // The caller must still reach somebody: an agent that will not start is a transfer.
            return CallFlowExit.TRANSFER;
        }
        call.markServed();
        call.setMissedReason(null);
        try {
            while (!call.isGone()) {
                try {
                    return result.get(200, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    // still talking
                } catch (ExecutionException e) {
                    return CallFlowExit.COMPLETED;
                }
            }
            return CallFlowExit.COMPLETED;
        } finally {
            ari.stopAgentOnFlowCall(call);
        }
    }

    /**
     * Waits the call in {@code queue} until an operator has talked to the caller and hung
     * up, the caller gives up or takes the callback, or the queue overflows. A queue made
     * up on the spot ({@code id == 0}) rings its members or {@code externalNumber} with a
     * ringback tone and none of the queue's announcements.
     */
    private QueueOutcome enqueueAndWait(FlowCall call, CallQueue queue, String externalNumber, boolean confirmExternal)
            throws InterruptedException {
        long companyId = call.companyId();
        if (queue == null || !queue.enabled()) {
            call.setMissedReason(MissedCallReason.NO_OPERATOR);
            return QueueOutcome.OVERFLOW;
        }
        boolean adHoc = queue.id() <= 0;
        if (!adHoc) {
            if (!dispatcher.anyMemberSignedIn(queue)) {
                call.setMissedReason(MissedCallReason.NO_OPERATOR);
                callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.OVERFLOW,
                        "queueId", queue.id(), "reason", "nobody signed in");
                return QueueOutcome.OVERFLOW;
            }
            if (queue.maxLength() > 0 && dispatcher.lineLength(queue.id()) >= queue.maxLength()) {
                call.setMissedReason(MissedCallReason.NO_OPERATOR);
                callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.OVERFLOW,
                        "queueId", queue.id(), "reason", "queue full");
                return QueueOutcome.OVERFLOW;
            }
        }
        String transcript = ariProvider.getObject().findHandoverTranscript(call.channelId());
        WaitingCall waiting = dispatcher.enqueue(call, queue, Language.DEFAULT.code(), transcript,
                externalNumber, confirmExternal);
        if (!adHoc) {
            call.setQueueId(queue.id());
            callAttemptUseCase.markEnqueued(call.callAttemptId(), queue.id());
            callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.ENQUEUED,
                    "queueId", queue.id(), "position", dispatcher.position(waiting));
        }
        Instant start = clock.instant();
        boolean announced = false;
        boolean offered = false;
        int lastPosition = -1;
        long nextPositionAt = Long.MAX_VALUE;
        long nextPeriodicAt = queue.periodicAnnounceEverySec() > 0
                ? System.currentTimeMillis() + queue.periodicAnnounceEverySec() * 1000L : Long.MAX_VALUE;
        long offerAt = queue.callbackOfferAfterSec() > 0
                ? System.currentTimeMillis() + queue.callbackOfferAfterSec() * 1000L : Long.MAX_VALUE;
        CompletableFuture<CallFlowExit> companion = !adHoc && queue.waitingAgentId() != null
                ? startCompanion(call, queue.waitingAgentId()) : null;
        try {
            while (true) {
                if (call.isGone()) {
                    int waited = (int) Duration.between(start, clock.instant()).toSeconds();
                    boolean talked = waiting.connectedAt() != null;
                    dispatcher.cancel(waiting);
                    if (!adHoc && !talked) {
                        callAttemptUseCase.markAbandoned(call.callAttemptId(), waited);
                        callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.ABANDONED,
                                "queueId", queue.id(), "waitSeconds", waited);
                        call.setMissedReason(MissedCallReason.ABANDONED);
                    }
                    return QueueOutcome.ABANDONED;
                }
                WaitingCallState state = waiting.state();
                if (state == WaitingCallState.CONNECTING) {
                    if (companion != null) {
                        ariProvider.getObject().stopAgentOnFlowCall(call);
                        companion = null;
                    }
                    prompter.stop(call);
                    Thread.sleep(callFlowProperties.connectingPollMs());
                    continue;
                }
                if (state == WaitingCallState.CONNECTED) {
                    if (call.onHold()) {
                        ensureHoldAudio(call, queue, false);
                    }
                    Thread.sleep(callFlowProperties.queuePollMs());
                    continue;
                }
                if (state == WaitingCallState.OPERATOR_LEFT) {
                    dispatcher.cancel(waiting);
                    CallTransferTarget transfer = waiting.transfer();
                    if (transfer != null) {
                        return transferTo(call, transfer);
                    }
                    if (!adHoc && queue.postCallSurvey()) {
                        survey(call);
                    }
                    return QueueOutcome.ANSWERED;
                }
                if (state == WaitingCallState.CANCELLED) {
                    return QueueOutcome.OVERFLOW;
                }
                long waitedMs = Duration.between(start, clock.instant()).toMillis();
                boolean forwardDone = externalNumber != null && waiting.externalTried()
                        && state == WaitingCallState.WAITING;
                if (waitedMs >= queue.maxWaitSec() * 1000L || forwardDone) {
                    dispatcher.cancel(waiting);
                    if (adHoc) {
                        return QueueOutcome.NO_ANSWER;
                    }
                    call.setMissedReason(MissedCallReason.NO_OPERATOR);
                    callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.OVERFLOW,
                            "queueId", queue.id(), "reason", "waited " + waitedMs / 1000 + "s");
                    return QueueOutcome.OVERFLOW;
                }
                if (companion != null) {
                    if (companion.isDone() && companion.getNow(CallFlowExit.TRANSFER) == CallFlowExit.COMPLETED) {
                        dispatcher.cancel(waiting);
                        call.markServed();
                        call.setMissedReason(null);
                        return QueueOutcome.RESOLVED_BY_AGENT;
                    }
                    Thread.sleep(callFlowProperties.queuePollMs());
                    continue;
                }
                if (!adHoc) {
                    long now = System.currentTimeMillis();
                    int currentPosition = dispatcher.position(waiting);
                    if (!announced) {
                        announced = true;
                        prompter.say(call, PbxPhrases.allOperatorsBusy(call.language()), false);
                        lastPosition = announcePosition(call, waiting, queue, lastPosition, true);
                        nextPositionAt = queue.announcePositionEverySec() > 0
                                ? now + queue.announcePositionEverySec() * 1000L : Long.MAX_VALUE;
                        continue;
                    }
                    if (now >= nextPositionAt) {
                        lastPosition = announcePosition(call, waiting, queue, lastPosition, true);
                        nextPositionAt = now + queue.announcePositionEverySec() * 1000L;
                        continue;
                    }
                    if (currentPosition > 0 && currentPosition != lastPosition) {
                        lastPosition = announcePosition(call, waiting, queue, lastPosition, false);
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
                }
                Character key = call.digits().poll(200, call::isGone);
                if (offered && key != null && key == '1') {
                    dispatcher.cancel(waiting);
                    prompter.say(call, PbxPhrases.callbackAccepted(call.language()), false);
                    call.setMissedReason(MissedCallReason.CALLBACK_REQUESTED);
                    callEventUseCase.record(companyId, call.callAttemptId(), CallEventType.CALLBACK_REQUESTED, "queueId", queue.id());
                    return QueueOutcome.CALLBACK;
                }
                if (waiting.state() == WaitingCallState.WAITING || waiting.state() == WaitingCallState.RINGING) {
                    ensureHoldAudio(call, queue, adHoc);
                }
            }
        } finally {
            if (companion != null) {
                ariProvider.getObject().stopAgentOnFlowCall(call);
            }
        }
    }

    /** Says the position when it changed since it was last said (or forced periodically); returns what was said. */
    private int announcePosition(FlowCall call, WaitingCall waiting, CallQueue queue, int lastPosition, boolean force) {
        int position = dispatcher.position(waiting);
        if (position <= 0 || (!force && position == lastPosition)) {
            return lastPosition;
        }
        Integer minutes = queue.announceWaitTime() ? dispatcher.estimateWaitMinutes(waiting) : null;
        prompter.say(call, PbxPhrases.queuePosition(position, minutes, call.language()), false);
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.POSITION_ANNOUNCED,
                "position", position, "waitMinutes", minutes);
        return position;
    }

    private void ensureHoldAudio(FlowCall call, CallQueue queue, boolean ringbackTone) {
        if (call.endpoint().isPlaying()) {
            return;
        }
        short[] audio;
        if (ringbackTone) {
            audio = ringback();
        } else if (queue.holdMusicFileId() != null) {
            audio = prompter.recording(call.companyId(), queue.holdMusicFileId());
            if (audio.length == 0) {
                audio = HoldMusicGenerator.melody();
            }
        } else {
            audio = HoldMusicGenerator.melody();
        }
        call.endpoint().playPcm(audio);
    }

    private short[] ringback() {
        short[] cached = ringback;
        if (cached == null) {
            cached = RingbackToneGenerator.generateRingback(6000);
            ringback = cached;
        }
        return cached;
    }

    /** An AI agent keeps the caller company while they wait; its "completed" means it sorted the matter out. */
    private CompletableFuture<CallFlowExit> startCompanion(FlowCall call, long agentId) {
        CompletableFuture<CallFlowExit> done = new CompletableFuture<>();
        boolean started = ariProvider.getObject().startAgentOnFlowCall(call, agentId,
                () -> done.complete(CallFlowExit.COMPLETED), () -> {
                    // the caller is already waiting for a person
                });
        return started ? done : null;
    }

    private QueueOutcome transferTo(FlowCall call, CallTransferTarget target) throws InterruptedException {
        return switch (target.type()) {
            case QUEUE -> waitInQueue(call, callQueueUseCase.findQueue(call.companyId(), target.targetId()));
            case USER -> ringUsers(call, List.of(target.targetId()), DEFAULT_RING_SEC, CallQueueStrategy.LEAST_RECENT);
            case EXTERNAL -> forward(call, target.number(), DEFAULT_RING_SEC, false);
        };
    }

    private static CallQueue adHocQueue(FlowCall call, CallQueueStrategy strategy, int ringSeconds,
                                        List<CallQueueMember> members) {
        return new CallQueue(0, call.companyId(), null, strategy, ringSeconds, 0, ringSeconds + 2, 0, 0, false, null, 0,
                null, 0, null, QueueOverflowAction.HANGUP, null, 20, 0, false, null, false, true, members, null);
    }

    private String applyOverflowAtDepth(FlowCall call, CallQueue queue, int depth) throws InterruptedException {
        if (queue == null || depth >= MAX_OVERFLOW_DEPTH || call.isGone()) {
            if (!call.isGone()) {
                prompter.say(call, PbxPhrases.overflow(call.language()), false);
            }
            return null;
        }
        Long target = queue.overflowTargetId();
        switch (queue.overflowAction()) {
            case VOICEMAIL -> {
                Long messageId = target != null ? voicemailRecorder.record(call, target) : null;
                if (messageId != null) {
                    call.setVoicemailMessageId(messageId);
                    call.setMissedReason(MissedCallReason.VOICEMAIL);
                }
            }
            case QUEUE -> {
                CallQueue next = target != null ? callQueueUseCase.findQueue(call.companyId(), target) : null;
                QueueOutcome outcome = waitInQueue(call, next);
                if (outcome == QueueOutcome.OVERFLOW) {
                    return applyOverflowAtDepth(call, next, depth + 1);
                }
                if (outcome == QueueOutcome.CALLBACK) {
                    queueCallback(call, next);
                }
            }
            case AI_AGENT -> {
                if (target != null) {
                    runAgent(call, target);
                }
            }
            case EXTERNAL_NUMBER -> forward(call, queue.overflowTarget(), DEFAULT_RING_SEC, false);
            case CALLBACK -> {
                prompter.say(call, PbxPhrases.overflow(call.language()), false);
                if (queue.callbackCampaignId() != null) {
                    queueCallback(call, queue);
                }
            }
            case HANGUP -> prompter.say(call, PbxPhrases.overflow(call.language()), false);
        }
        return null;
    }

    private void survey(FlowCall call) throws InterruptedException {
        if (call.isGone()) {
            return;
        }
        call.digits().clear();
        prompter.say(call, PbxPhrases.survey(call.language()), true);
        Character key = call.digits().poll(SURVEY_ANSWER_MS, call::isGone);
        if (key == null || key < '1' || key > '5') {
            return;
        }
        int score = key - '0';
        callAttemptUseCase.recordSurveyScore(call.callAttemptId(), score);
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.SURVEY_SCORE,
                "score", score, "userId", call.operatorUserId());
        if (score <= LOW_SURVEY_SCORE) {
            notificationUseCase.notify(call.companyId(), NotificationType.LOW_SURVEY_SCORE, "Past baho",
                    (call.callerNumber() != null ? call.callerNumber() : "Mijoz") + " operator suhbatini "
                            + score + " ga baholadi", null);
        }
        prompter.say(call, PbxPhrases.surveyThanks(call.language()), false);
    }
}
