package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.ari.AriService;
import uz.murodjon.robotcallv2.agent.audio.RingbackToneGenerator;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.queue.CallQueueDispatcher;
import uz.murodjon.robotcallv2.agent.queue.HoldMusicGenerator;
import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;
import uz.murodjon.robotcallv2.agent.queue.WaitingCall;
import uz.murodjon.robotcallv2.agent.queue.WaitingCallState;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProvider;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowExit;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTransferTarget;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessHoursVerdict;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;

import java.io.Closeable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Walks an inbound call through its call flow (docs/INBOUND-PBX-PLAN.md §4): one node
 * after another on the call's own virtual thread, each deciding which exit it leaves by,
 * until a node has no exit or the caller hangs up. Also waits a call in an operator queue
 * — hold music, "sizning navbatingiz N-chi", the callback offer, the survey after the
 * operator — for flow and non-flow calls alike.
 *
 * <p>An exit that names no node ends the call; so does a flow that keeps going round for
 * {@value #MAX_STEPS} steps, which is a menu looping into itself, not a caller still
 * choosing.
 */
@Component
public class CallFlowRunner {

    private static final Logger log = LoggerFactory.getLogger(CallFlowRunner.class);
    static final int MAX_STEPS = 300;
    private static final int DEFAULT_MENU_TIMEOUT_SEC = 6;
    private static final int DEFAULT_MENU_RETRIES = 2;
    private static final int DEFAULT_LANGUAGE_TIMEOUT_SEC = 5;
    private static final int DEFAULT_RING_SEC = 25;
    private static final long INTER_DIGIT_MS = 1500;
    private static final long CALLBACK_ANSWER_MS = 7000;
    private static final long SURVEY_ANSWER_MS = 8000;
    private static final int MAX_OVERFLOW_DEPTH = 3;
    private static final int LOW_SURVEY_SCORE = 2;
    private static final Duration REPEAT_CALLER_WINDOW = Duration.ofHours(24);
    private static final String AFTER_HOURS_TOKEN = "{afterHours}";
    private static final String FALLBACK_LANGUAGE = "uz-UZ";

    private final FlowPrompter prompter;
    private final VoicemailRecorder voicemailRecorder;
    private final FlowWebhookClient webhookClient;
    private final CallQueueDispatcher dispatcher;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final MissedCallUseCase missedCallUseCase;
    private final CallEventUseCase callEventUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final UserUseCase userUseCase;
    private final NotificationUseCase notificationUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    private final CompanyConfigUseCase companyConfig;
    private final SttProviderSelector sttProviderSelector;
    private final SttProperties sttProperties;
    private final VoiceMetrics metrics;
    private final ObjectProvider<AriService> ariProvider;
    private final Clock clock;
    private final CallFlowProperties callFlowProperties;
    private volatile short[] ringback;

    /** What a flow remembers between nodes: the last schedule it asked, for "{afterHours}". */
    private static final class FlowState {
        private BusinessHoursVerdict lastVerdict;
        private ZoneId zone;
    }

    public CallFlowRunner(FlowPrompter prompter, VoicemailRecorder voicemailRecorder, FlowWebhookClient webhookClient,
                          CallQueueDispatcher dispatcher, BusinessScheduleUseCase businessScheduleUseCase, CallQueueUseCase callQueueUseCase,
                          AiAgentUseCase aiAgentUseCase, MissedCallUseCase missedCallUseCase, CallEventUseCase callEventUseCase,
                          CallAttemptUseCase callAttemptUseCase, UserUseCase userUseCase, NotificationUseCase notificationUseCase,
                          CampaignTargetUseCase campaignTargetUseCase, CompanyConfigUseCase companyConfig,
                          SttProviderSelector sttProviderSelector, SttProperties sttProperties, VoiceMetrics metrics,
                          ObjectProvider<AriService> ariProvider, Clock clock,
                          CallFlowProperties callFlowProperties) {
        this.prompter = prompter;
        this.voicemailRecorder = voicemailRecorder;
        this.webhookClient = webhookClient;
        this.dispatcher = dispatcher;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.missedCallUseCase = missedCallUseCase;
        this.callEventUseCase = callEventUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.userUseCase = userUseCase;
        this.notificationUseCase = notificationUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.companyConfig = companyConfig;
        this.sttProviderSelector = sttProviderSelector;
        this.sttProperties = sttProperties;
        this.metrics = metrics;
        this.ariProvider = ariProvider;
        this.clock = clock;
        this.callFlowProperties = callFlowProperties;
    }

    /** Runs {@code graph} from its entry; returns when the call is over for the PBX side. */
    public void run(FlowCall call, CallFlowGraph graph, Long flowId) {
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.FLOW_ENTERED, "flowId", flowId);
        FlowState state = new FlowState();
        String nodeId = graph.entry();
        int steps = 0;
        try {
            while (nodeId != null && !call.isGone()) {
                if (++steps > MAX_STEPS) {
                    log.warn("[{}] flow {} ran {} steps without ending — hanging up", call.channelId(), flowId, MAX_STEPS);
                    break;
                }
                CallFlowNode node = graph.node(nodeId);
                if (node == null) {
                    break;
                }
                callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.NODE_ENTERED,
                        "node", nodeId, "type", node.type().name());
                nodeId = execute(call, node, state);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[{}] call flow failed: {}", call.channelId(), e.getMessage(), e);
        } finally {
            finish(call);
        }
    }

    /**
     * Waits a call in {@code queueId} outside any flow — an AI agent handing over to its
     * transfer queue — with the queue's own overflow afterwards.
     */
    public void runQueue(FlowCall call, long queueId) {
        try {
            CallQueue queue = callQueueUseCase.findQueue(call.companyId(), queueId);
            QueueOutcome outcome = waitInQueue(call, queue, null, false);
            if (outcome == QueueOutcome.OVERFLOW) {
                applyOverflow(call, queue, 0);
            } else if (outcome == QueueOutcome.CALLBACK) {
                queueCallback(call, queue);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("[{}] queue {} failed: {}", call.channelId(), queueId, e.getMessage(), e);
        } finally {
            finish(call);
        }
    }

    private String execute(FlowCall call, CallFlowNode node, FlowState state) throws InterruptedException {
        return switch (node.type()) {
            case PLAY -> {
                playPrompt(call, node, state, false);
                yield node.exit(CallFlowExit.NEXT);
            }
            case SCHEDULE -> schedule(call, node, state);
            case MENU -> menu(call, node, state);
            case LANGUAGE_SELECT -> languageSelect(call, node, state);
            case AI_AGENT -> {
                CallFlowExit exit = runAgent(call, node.aiAgentId());
                if (exit == CallFlowExit.TRANSFER && node.exit(CallFlowExit.TRANSFER) == null) {
                    Long queueId = agentTransferQueue(call, node.aiAgentId());
                    if (queueId != null) {
                        queueInline(call, queueId);
                        yield null;
                    }
                }
                yield node.exit(exit);
            }
            case QUEUE -> {
                CallQueue queue = callQueueUseCase.findQueue(call.companyId(), node.queueId());
                QueueOutcome outcome = waitInQueue(call, queue, null, false);
                yield switch (outcome) {
                    case ANSWERED, RESOLVED_BY_AGENT -> node.exit(CallFlowExit.ANSWERED);
                    case CALLBACK -> {
                        queueCallback(call, queue);
                        yield node.exit(CallFlowExit.CALLBACK);
                    }
                    case OVERFLOW -> node.exit(CallFlowExit.OVERFLOW) != null
                            ? node.exit(CallFlowExit.OVERFLOW) : applyOverflow(call, queue, 0);
                    case ABANDONED, NO_ANSWER -> null;
                };
            }
            case RING_GROUP, EXTENSION -> {
                QueueOutcome outcome = ringUsers(call, node.userIds(), ringSeconds(node),
                        node.type() == uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType.RING_GROUP
                                ? CallQueueStrategy.RING_ALL : CallQueueStrategy.LEAST_RECENT);
                yield exitFor(node, outcome);
            }
            case FORWARD_EXTERNAL -> exitFor(node,
                    forward(call, node.phoneNumber(), ringSeconds(node), Boolean.TRUE.equals(node.confirmAnswer())));
            case VOICEMAIL -> {
                Long messageId = voicemailRecorder.record(call, node.voicemailBoxId());
                if (messageId != null) {
                    call.setVoicemailMessageId(messageId);
                    call.setMissedReason(MissedCallReason.VOICEMAIL);
                }
                yield node.exit(CallFlowExit.NEXT);
            }
            case CALLBACK_OFFER -> callbackOffer(call, node, state);
            case CALLER_ROUTER -> callerRouter(call, node);
            case WEBHOOK_ROUTER -> {
                String answer = webhookClient.askExit(node.url(), call);
                if (answer == null) {
                    String error = node.exit(CallFlowExit.ERROR);
                    yield error != null ? error : node.exit(CallFlowExit.DEFAULT);
                }
                String target = node.exits().get(answer);
                yield target != null ? target : node.exit(CallFlowExit.DEFAULT);
            }
            case HANGUP -> null;
        };
    }

    private String schedule(FlowCall call, CallFlowNode node, FlowState state) {
        BusinessHoursVerdict verdict = businessScheduleUseCase.evaluate(call.companyId(), node.scheduleId(), clock.instant());
        state.lastVerdict = verdict;
        state.zone = businessScheduleUseCase.findZone(call.companyId(), node.scheduleId());
        if (verdict.open()) {
            return node.exit(CallFlowExit.OPEN);
        }
        call.setMissedReason(MissedCallReason.AFTER_HOURS);
        if (verdict.holidayName() != null && node.exit(CallFlowExit.HOLIDAY) != null) {
            return node.exit(CallFlowExit.HOLIDAY);
        }
        return node.exit(CallFlowExit.CLOSED);
    }

    private String menu(FlowCall call, CallFlowNode node, FlowState state) throws InterruptedException {
        int retries = node.maxRetries() != null ? node.maxRetries() : DEFAULT_MENU_RETRIES;
        long timeoutMs = (node.timeoutSec() != null ? node.timeoutSec() : DEFAULT_MENU_TIMEOUT_SEC) * 1000L;
        MenuListener speech = node.keywords().isEmpty() ? null : MenuListener.open(this, call);
        try {
            for (int attempt = 0; attempt <= retries && !call.isGone(); attempt++) {
                playPrompt(call, node, state, true);
                String entry = readMenuEntry(call, node, timeoutMs, speech);
                if (entry.isEmpty()) {
                    if (attempt < retries) {
                        prompter.say(call, PbxPhrases.noInput(call.language()), false);
                        continue;
                    }
                    return node.exit(CallFlowExit.TIMEOUT);
                }
                String target = node.options().get(entry);
                if (target != null) {
                    call.addChoice(choiceLabel(node, entry));
                    callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.DTMF, "key", entry);
                    return target;
                }
                if (node.extensionDigits() != null && entry.length() == node.extensionDigits()) {
                    User user = userUseCase.findBySipExtension(call.companyId(), entry);
                    if (user != null) {
                        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.DTMF,
                                "key", entry, "extension", true);
                        QueueOutcome outcome = ringUsers(call, List.of(user.id()), DEFAULT_RING_SEC,
                                CallQueueStrategy.LEAST_RECENT);
                        if (outcome == QueueOutcome.ANSWERED || outcome == QueueOutcome.ABANDONED) {
                            return null;
                        }
                    }
                }
                if (attempt < retries) {
                    prompter.say(call, PbxPhrases.invalidOption(call.language()), false);
                    continue;
                }
                String invalid = node.exit(CallFlowExit.INVALID);
                return invalid != null ? invalid : node.exit(CallFlowExit.TIMEOUT);
            }
            return null;
        } finally {
            if (speech != null) {
                speech.close();
            }
        }
    }

    /** One key — or, where extensions may be dialled, a few — or a spoken option; empty on silence. */
    private String readMenuEntry(FlowCall call, CallFlowNode node, long timeoutMs, MenuListener speech)
            throws InterruptedException {
        int maxDigits = node.extensionDigits() != null ? node.extensionDigits() : 1;
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!call.isGone() && System.currentTimeMillis() < deadline) {
            Character key = call.digits().poll(100, call::isGone);
            if (key != null) {
                String entry = String.valueOf(key);
                if (maxDigits > 1 && key != '*' && key != '#') {
                    // A menu key and the first digit of an extension look the same until the
                    // next key — or a short pause — says which it was.
                    entry += call.digits().collect(maxDigits - 1, INTER_DIGIT_MS, INTER_DIGIT_MS, call::isGone);
                }
                return entry;
            }
            if (speech != null) {
                String spoken = speech.poll();
                if (spoken != null) {
                    String option = matchKeyword(node, spoken);
                    if (option != null) {
                        return option;
                    }
                }
            }
        }
        return "";
    }

    private String languageSelect(FlowCall call, CallFlowNode node, FlowState state) throws InterruptedException {
        if (Boolean.TRUE.equals(node.skipWhenKnown()) && call.languageKnown()) {
            return node.exit(CallFlowExit.NEXT);
        }
        long timeoutMs = (node.timeoutSec() != null ? node.timeoutSec() : DEFAULT_LANGUAGE_TIMEOUT_SEC) * 1000L;
        playPrompt(call, node, state, true);
        Character key = call.digits().poll(timeoutMs, call::isGone);
        String language = key != null ? node.options().get(String.valueOf(key)) : null;
        if (language == null) {
            language = node.language();
        }
        if (language != null) {
            call.setLanguage(language);
            callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.LANGUAGE_SET, "language", language);
        }
        return node.exit(CallFlowExit.NEXT);
    }

    private String callbackOffer(FlowCall call, CallFlowNode node, FlowState state) throws InterruptedException {
        call.digits().clear();
        boolean spoken = playPrompt(call, node, state, true)
                || node.audioFileId() != null || node.promptFor(call.language()) != null;
        if (!spoken) {
            prompter.say(call, PbxPhrases.callbackQuestion(call.language()), true);
        }
        Character key = call.digits().poll(CALLBACK_ANSWER_MS, call::isGone);
        if (key != null && key == '1') {
            call.setMissedReason(MissedCallReason.CALLBACK_REQUESTED);
            callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.CALLBACK_REQUESTED);
            prompter.say(call, PbxPhrases.callbackAccepted(call.language()), false);
            return node.exit(CallFlowExit.ACCEPTED);
        }
        return node.exit(CallFlowExit.DECLINED);
    }

    private String callerRouter(FlowCall call, CallFlowNode node) {
        if (call.vip() && node.exit(CallFlowExit.VIP) != null) {
            return node.exit(CallFlowExit.VIP);
        }
        if (call.clientName() != null && node.exit(CallFlowExit.KNOWN) != null) {
            return node.exit(CallFlowExit.KNOWN);
        }
        if (node.exit(CallFlowExit.REPEAT) != null && repeatCaller(call)) {
            return node.exit(CallFlowExit.REPEAT);
        }
        return node.exit(CallFlowExit.DEFAULT);
    }

    /** Rang in the last day and got through — ringing again usually means it was not sorted out. */
    private boolean repeatCaller(FlowCall call) {
        if (call.callerNumber() == null) {
            return false;
        }
        Instant now = clock.instant();
        return callAttemptUseCase.findAnsweredCalls(call.companyId(), call.callerNumber(), now.minus(REPEAT_CALLER_WINDOW), now)
                .stream().anyMatch(answered -> answered.callAttemptId() != call.callAttemptId());
    }

    /** Hands the call to an AI agent until it finishes or asks for a person. */
    private CallFlowExit runAgent(FlowCall call, long agentId) throws InterruptedException {
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

    private Long agentTransferQueue(FlowCall call, long agentId) {
        try {
            return aiAgentUseCase.requirePublishedAgent(call.companyId(), agentId).callBehaviour().transferQueueId();
        } catch (Exception e) {
            return null;
        }
    }

    private void queueInline(FlowCall call, long queueId) throws InterruptedException {
        CallQueue queue = callQueueUseCase.findQueue(call.companyId(), queueId);
        QueueOutcome outcome = waitInQueue(call, queue, null, false);
        if (outcome == QueueOutcome.OVERFLOW) {
            applyOverflow(call, queue, 0);
        } else if (outcome == QueueOutcome.CALLBACK) {
            queueCallback(call, queue);
        }
    }

    /**
     * Waits the call in {@code queue} until an operator has talked to the caller and hung
     * up, the caller gives up or takes the callback, or the queue overflows. A queue made
     * up on the spot ({@code id == 0}) rings its members or {@code externalNumber} with a
     * ringback tone and none of the queue's announcements.
     */
    private QueueOutcome waitInQueue(FlowCall call, CallQueue queue, String externalNumber, boolean confirmExternal)
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
        WaitingCall waiting = dispatcher.enqueue(call, queue, companyLanguage(companyId), transcript,
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
                    if (!announced) {
                        announced = true;
                        prompter.say(call, PbxPhrases.allOperatorsBusy(call.language()), false);
                        lastPosition = announcePosition(call, waiting, queue, lastPosition);
                        nextPositionAt = queue.announcePositionEverySec() > 0
                                ? now + queue.announcePositionEverySec() * 1000L : Long.MAX_VALUE;
                        continue;
                    }
                    if (now >= nextPositionAt) {
                        lastPosition = announcePosition(call, waiting, queue, lastPosition);
                        nextPositionAt = now + queue.announcePositionEverySec() * 1000L;
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

    /** Says the position when it changed since it was last said; returns what was said. */
    private int announcePosition(FlowCall call, WaitingCall waiting, CallQueue queue, int lastPosition) {
        int position = dispatcher.position(waiting);
        if (position <= 0 || position == lastPosition) {
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
            case QUEUE -> waitInQueue(call, callQueueUseCase.findQueue(call.companyId(), target.targetId()), null, false);
            case USER -> ringUsers(call, List.of(target.targetId()), DEFAULT_RING_SEC, CallQueueStrategy.LEAST_RECENT);
            case EXTERNAL -> forward(call, target.number(), DEFAULT_RING_SEC, false);
        };
    }

    private QueueOutcome ringUsers(FlowCall call, List<Long> userIds, int ringSeconds, CallQueueStrategy strategy)
            throws InterruptedException {
        List<CallQueueMember> members = userIds.stream().map(id -> new CallQueueMember(id, 0, Set.of())).toList();
        return waitInQueue(call, adHocQueue(call, strategy, ringSeconds, members), null, false);
    }

    private QueueOutcome forward(FlowCall call, String number, int ringSeconds, boolean confirm)
            throws InterruptedException {
        return waitInQueue(call, adHocQueue(call, CallQueueStrategy.RING_ALL, ringSeconds, List.of()), number, confirm);
    }

    private static CallQueue adHocQueue(FlowCall call, CallQueueStrategy strategy, int ringSeconds,
                                        List<CallQueueMember> members) {
        return new CallQueue(0, call.companyId(), null, strategy, ringSeconds, 0, ringSeconds + 2, 0, 0, false, null, 0,
                null, 0, null, QueueOverflowAction.HANGUP, null, 20, 0, false, null, false, true, members, null);
    }

    /** A queue the flow gave no overflow exit: its own overflow setting decides. Always ends the call. */
    private String applyOverflow(FlowCall call, CallQueue queue, int depth) throws InterruptedException {
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
                QueueOutcome outcome = waitInQueue(call, next, null, false);
                if (outcome == QueueOutcome.OVERFLOW) {
                    return applyOverflow(call, next, depth + 1);
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

    /**
     * A caller who asked to be called back: onto the queue's callback campaign when it has
     * one (the AI calls back), otherwise the missed-call list keeps them for the operators.
     */
    private void queueCallback(FlowCall call, CallQueue queue) {
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

    /** Whoever the call was not served by goes on the operators' list. */
    private void finish(FlowCall call) {
        MissedCallReason reason = call.missedReason();
        if (reason == null || call.operatorUserId() != null) {
            return;
        }
        try {
            missedCallUseCase.record(call.companyId(), new MissedCallOccurrence(call.callerNumber(), reason, call.queueId(),
                    call.inboundRouteId(), call.callAttemptId() != 0 ? call.callAttemptId() : null,
                    call.voicemailMessageId()));
        } catch (Exception e) {
            log.warn("[{}] missed call not recorded: {}", call.channelId(), e.getMessage());
        }
    }

    private boolean playPrompt(FlowCall call, CallFlowNode node, FlowState state, boolean interruptible) {
        if (node.audioFileId() != null) {
            return prompter.play(call, prompter.recording(call.companyId(), node.audioFileId()), interruptible);
        }
        String text = node.promptFor(call.language());
        if (text == null || text.isBlank()) {
            return false;
        }
        if (text.contains(AFTER_HOURS_TOKEN)) {
            text = text.replace(AFTER_HOURS_TOKEN, afterHoursText(call, state));
        }
        return prompter.say(call, text, interruptible);
    }

    private String afterHoursText(FlowCall call, FlowState state) {
        if (state.lastVerdict == null || state.zone == null) {
            return "";
        }
        return PbxPhrases.afterHours(call.language(), clock.instant().atZone(state.zone),
                state.lastVerdict.nextOpeningAt(), state.lastVerdict.holidayName());
    }

    private static String exitFor(CallFlowNode node, QueueOutcome outcome) {
        return switch (outcome) {
            case ANSWERED, RESOLVED_BY_AGENT -> node.exit(CallFlowExit.ANSWERED);
            case ABANDONED -> null;
            default -> node.exit(CallFlowExit.NO_ANSWER);
        };
    }

    private static int ringSeconds(CallFlowNode node) {
        return node.timeoutSec() != null ? node.timeoutSec() : DEFAULT_RING_SEC;
    }

    /** What the operator hears the caller chose — the option's first keyword, else the key itself. */
    private static String choiceLabel(CallFlowNode node, String key) {
        List<String> words = node.keywords().get(key);
        return words != null && !words.isEmpty() ? words.get(0) : key;
    }

    static String matchKeyword(CallFlowNode node, String spoken) {
        String heard = normalize(spoken);
        for (Map.Entry<String, List<String>> option : node.keywords().entrySet()) {
            for (String word : option.getValue()) {
                String keyword = normalize(word);
                if (!keyword.isBlank() && heard.contains(keyword)) {
                    return option.getKey();
                }
            }
        }
        return null;
    }

    /** Lower case, and every apostrophe Uzbek is typed with made one: o‘, o', oʻ and o` are one letter. */
    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase().replaceAll("[‘’`ʻʼ]", "'");
    }

    private String companyLanguage(long companyId) {
        try {
            return companyConfig.resolveLanguage(companyId, null);
        } catch (Exception e) {
            return FALLBACK_LANGUAGE;
        }
    }

    /** A recognizer that listens while a spoken menu waits, handing finished phrases to the menu. */
    private static final class MenuListener implements Closeable {
        private final FlowCall call;
        private final SttStreamBridge bridge;
        private final BlockingQueue<String> phrases;

        private MenuListener(FlowCall call, SttStreamBridge bridge, BlockingQueue<String> phrases) {
            this.call = call;
            this.bridge = bridge;
            this.phrases = phrases;
        }

        static MenuListener open(CallFlowRunner runner, FlowCall call) {
            if (!runner.sttProperties.enabled()) {
                return null;
            }
            try {
                SttProvider provider = runner.sttProviderSelector.findForCall(null);
                if (provider == null) {
                    return null;
                }
                BlockingQueue<String> phrases = new LinkedBlockingQueue<>();
                SttStreamBridge bridge = new SttStreamBridge(provider, provider.sampleRate(), 8000, call.channelId(),
                        call.language(), List.of(), (text, isFinal, confidence) -> {
                            if (isFinal && text != null && !text.isBlank()) {
                                phrases.offer(text);
                            }
                        }, null, runner.metrics, null, runner.sttProperties.responseTimeoutMs(), null, List.of(),
                        runner.sttProviderSelector, null, runner.sttProperties.stream());
                call.attachListener(bridge);
                return new MenuListener(call, bridge, phrases);
            } catch (Exception e) {
                log.warn("[{}] spoken menu unavailable, keys only: {}", call.channelId(), e.getMessage());
                return null;
            }
        }

        String poll() {
            return phrases.poll();
        }

        @Override
        public void close() {
            call.detachListener(bridge);
            bridge.close();
        }
    }
}
