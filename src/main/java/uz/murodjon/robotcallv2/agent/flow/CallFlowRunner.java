package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.queue.QueueOutcome;
import uz.murodjon.robotcallv2.agent.telephony.TelephonyGatewayResolver;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowExit;
import uz.murodjon.robotcallv2.callflow.domain.enums.CallFlowNodeType;
import uz.murodjon.robotcallv2.callqueue.application.port.input.CallQueueUseCase;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.schedule.application.port.input.BusinessScheduleUseCase;
import uz.murodjon.robotcallv2.schedule.domain.entity.BusinessHoursVerdict;
import uz.murodjon.robotcallv2.shared.dialog.TelephonyEngine;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;
import uz.murodjon.robotcallv2.user.domain.entity.User;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * Walks an inbound call through its call flow (docs/INBOUND-PBX-PLAN.md §4): one node
 * after another on the call's own virtual thread, each deciding which exit it leaves by,
 * until a node has no exit or the caller hangs up. Getting the caller to a person — queues,
 * ringing, overflow, an AI agent — is {@link FlowQueueRunner}'s; reading a menu choice is
 * {@link FlowMenuReader}'s.
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
    private static final long CALLBACK_ANSWER_MS = 7000;
    private static final Duration REPEAT_CALLER_WINDOW = Duration.ofHours(24);
    private static final String AFTER_HOURS_TOKEN = "{afterHours}";

    private final FlowPrompter prompter;
    private final VoicemailRecorder voicemailRecorder;
    private final FlowWebhookClient webhookClient;
    private final FlowQueueRunner queueRunner;
    private final FlowMenuReader menuReader;
    private final BusinessScheduleUseCase businessScheduleUseCase;
    private final CallQueueUseCase callQueueUseCase;
    private final AiAgentUseCase aiAgentUseCase;
    private final MissedCallUseCase missedCallUseCase;
    private final CallEventUseCase callEventUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final UserUseCase userUseCase;
    private final Clock clock;
    private final TelephonyGatewayResolver telephonyGatewayResolver;
    private final FlowPeople flowPeople;
    private final FlowQueueLine queueLine;

    /** What a flow remembers between nodes: the last schedule it asked, for "{afterHours}". */
    private static final class FlowState {
        private BusinessHoursVerdict lastVerdict;
        private ZoneId zone;
    }

    public CallFlowRunner(FlowPrompter prompter, VoicemailRecorder voicemailRecorder, FlowWebhookClient webhookClient,
                          FlowQueueRunner queueRunner, FlowMenuReader menuReader,
                          BusinessScheduleUseCase businessScheduleUseCase, CallQueueUseCase callQueueUseCase,
                          AiAgentUseCase aiAgentUseCase, MissedCallUseCase missedCallUseCase, CallEventUseCase callEventUseCase,
                          CallAttemptUseCase callAttemptUseCase, UserUseCase userUseCase, Clock clock,
                          TelephonyGatewayResolver telephonyGatewayResolver, FlowPeople flowPeople,
                          FlowQueueLine queueLine) {
        this.queueLine = queueLine;
        this.flowPeople = flowPeople;
        this.telephonyGatewayResolver = telephonyGatewayResolver;
        this.prompter = prompter;
        this.voicemailRecorder = voicemailRecorder;
        this.webhookClient = webhookClient;
        this.queueRunner = queueRunner;
        this.menuReader = menuReader;
        this.businessScheduleUseCase = businessScheduleUseCase;
        this.callQueueUseCase = callQueueUseCase;
        this.aiAgentUseCase = aiAgentUseCase;
        this.missedCallUseCase = missedCallUseCase;
        this.callEventUseCase = callEventUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.userUseCase = userUseCase;
        this.clock = clock;
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
            queueRunner.waitAndSettle(call, queueId);
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
                CallFlowExit exit = queueRunner.runAgent(call, node.aiAgentId());
                if (exit == CallFlowExit.TRANSFER && node.exit(CallFlowExit.TRANSFER) == null) {
                    Long queueId = agentTransferQueue(call, node.aiAgentId());
                    if (queueId != null && isAsterisk(call)) {
                        queueRunner.waitAndSettle(call, queueId);
                        yield null;
                    }
                    if (!isAsterisk(call)) {
                        yield queueId != null ? lineExit(call, node, callQueueUseCase.findQueue(call.companyId(), queueId))
                                : withoutPeople(call, node);
                    }
                }
                yield node.exit(exit);
            }
            case QUEUE -> {
                CallQueue queue = callQueueUseCase.findQueue(call.companyId(), node.queueId());
                QueueOutcome outcome = isAsterisk(call) ? queueRunner.waitInQueue(call, queue)
                        : queueLine.waitInQueue(call, queue);
                yield switch (outcome) {
                    case ANSWERED, RESOLVED_BY_AGENT -> node.exit(CallFlowExit.ANSWERED);
                    case CALLBACK -> {
                        queueRunner.queueCallback(call, queue);
                        yield node.exit(CallFlowExit.CALLBACK);
                    }
                    case OVERFLOW -> node.exit(CallFlowExit.OVERFLOW) != null
                            ? node.exit(CallFlowExit.OVERFLOW)
                            : isAsterisk(call) ? queueRunner.applyOverflow(call, queue) : withoutPeople(call, node);
                    case ABANDONED, NO_ANSWER -> null;
                };
            }
            case RING_GROUP, EXTENSION -> {
                if (!isAsterisk(call)) {
                    yield peopleExit(call, node, flowPeople.ringUsers(call, node.userIds(), ringSeconds(node)));
                }
                QueueOutcome outcome = queueRunner.ringUsers(call, node.userIds(), ringSeconds(node),
                        node.type() == CallFlowNodeType.RING_GROUP ? CallQueueStrategy.RING_ALL : CallQueueStrategy.LEAST_RECENT);
                yield exitFor(node, outcome);
            }
            case FORWARD_EXTERNAL -> !isAsterisk(call)
                    ? peopleExit(call, node, flowPeople.forward(call, node.phoneNumber(), ringSeconds(node))) : exitFor(node,
                    queueRunner.forward(call, node.phoneNumber(), ringSeconds(node), Boolean.TRUE.equals(node.confirmAnswer())));
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
        try (MenuInput input = menuReader.open(call, node)) {
            for (int attempt = 0; attempt <= retries && !call.isGone(); attempt++) {
                playPrompt(call, node, state, true);
                String entry = input.readEntry(timeoutMs);
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
                        QueueOutcome outcome = isAsterisk(call)
                                ? queueRunner.ringUsers(call, List.of(user.id()), FlowQueueRunner.DEFAULT_RING_SEC,
                                CallQueueStrategy.LEAST_RECENT)
                                : flowPeople.ringUsers(call, List.of(user.id()), FlowQueueRunner.DEFAULT_RING_SEC);
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
        }
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

    /**
     * Whether this call is on Asterisk, whose queues, rings and forwards {@link FlowQueueRunner} runs;
     * any other engine reaches people through {@link FlowPeople}.
     */
    private boolean isAsterisk(FlowCall call) {
        return telephonyGatewayResolver.findByCallId(call.channelId()).findEngine() == TelephonyEngine.ASTERISK;
    }

    /** An AI agent's hand-over to its transfer queue on an engine without a dispatcher: the waiting line, then where the flow goes. */
    private String lineExit(FlowCall call, CallFlowNode node, CallQueue queue) throws InterruptedException {
        QueueOutcome outcome = queueLine.waitInQueue(call, queue);
        return switch (outcome) {
            case ANSWERED, RESOLVED_BY_AGENT, ABANDONED -> null;
            case CALLBACK -> {
                queueRunner.queueCallback(call, queue);
                yield null;
            }
            default -> withoutPeople(call, node);
        };
    }

    /** Where a flow goes after {@link FlowPeople} tried a person; nobody taking the call is {@link #withoutPeople}. */
    private String peopleExit(FlowCall call, CallFlowNode node, QueueOutcome outcome) throws InterruptedException {
        return switch (outcome) {
            case ANSWERED, RESOLVED_BY_AGENT -> node.exit(CallFlowExit.ANSWERED);
            case ABANDONED -> null;
            default -> withoutPeople(call, node);
        };
    }

    /** A node whose people did not take the call: its no-answer exit, or the caller is told and let go. */
    private String withoutPeople(FlowCall call, CallFlowNode node) throws InterruptedException {
        log.info("[{}] nobody took the call at {} node — taking its no-answer exit", call.channelId(), node.type());
        call.setMissedReason(MissedCallReason.NO_OPERATOR);
        String exit = node.exit(CallFlowExit.NO_ANSWER) != null ? node.exit(CallFlowExit.NO_ANSWER)
                : node.exit(CallFlowExit.OVERFLOW);
        if (exit == null) {
            prompter.say(call, PbxPhrases.overflow(call.language()), false);
        }
        return exit;
    }

    private Long agentTransferQueue(FlowCall call, long agentId) {
        try {
            return aiAgentUseCase.requirePublishedAgent(call.companyId(), agentId).callBehaviour().transferQueueId();
        } catch (Exception e) {
            return null;
        }
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
        return node.timeoutSec() != null ? node.timeoutSec() : FlowQueueRunner.DEFAULT_RING_SEC;
    }

    /** What the operator hears the caller chose — the option's first keyword, else the key itself. */
    private static String choiceLabel(CallFlowNode node, String key) {
        List<String> words = node.keywords().get(key);
        return words != null && !words.isEmpty() ? words.get(0) : key;
    }
}
