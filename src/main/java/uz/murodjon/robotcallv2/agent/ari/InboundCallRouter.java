package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.flow.InboundCallGate;
import uz.murodjon.robotcallv2.agent.flow.InboundDecision;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundCallerRule;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.missedcall.application.port.input.MissedCallUseCase;
import uz.murodjon.robotcallv2.missedcall.domain.entity.MissedCallOccurrence;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.util.PhoneMasker;

import java.time.Duration;
import java.time.Instant;

/**
 * What an inbound call gets before any media is set up ({@link InboundCallGate}): refused,
 * told the line is closed, handed to a call flow, or — the only case that continues into the
 * agent's media setup — answered by the route's AI agent.
 */
@Service
class InboundCallRouter {

    private static final Logger log = LoggerFactory.getLogger(InboundCallRouter.class);

    /** Decides what an inbound call gets: a call flow, the route's agent, a closed message, a refusal. */
    private final InboundCallGate inboundCallGate;
    private final CallAttemptUseCase callAttemptUseCase;
    private final CallEventUseCase callEventUseCase;
    private final MissedCallUseCase missedCallUseCase;
    private final TtsProperties ttsProperties;
    private final TtsRouter ttsRouter;
    private final AriClient ariClient;
    private final CallMediaFactory callMediaFactory;
    private final FlowCallService flowCallService;
    private final FlowCallRegistry flowCallRegistry;

    InboundCallRouter(InboundCallGate inboundCallGate,
                      CallAttemptUseCase callAttemptUseCase,
                      CallEventUseCase callEventUseCase,
                      MissedCallUseCase missedCallUseCase,
                      TtsProperties ttsProperties,
                      TtsRouter ttsRouter,
                      AriClient ariClient,
                      CallMediaFactory callMediaFactory,
                      FlowCallService flowCallService,
                      FlowCallRegistry flowCallRegistry) {
        this.inboundCallGate = inboundCallGate;
        this.callAttemptUseCase = callAttemptUseCase;
        this.callEventUseCase = callEventUseCase;
        this.missedCallUseCase = missedCallUseCase;
        this.ttsProperties = ttsProperties;
        this.ttsRouter = ttsRouter;
        this.ariClient = ariClient;
        this.callMediaFactory = callMediaFactory;
        this.flowCallService = flowCallService;
        this.flowCallRegistry = flowCallRegistry;
    }

    /**
     * Decides an inbound call and carries out every verdict but one: the route whose agent
     * answers the call is returned for the media setup to continue with. Null when the call
     * was handled here — refused, closed, hung up, or run through its call flow.
     */
    InboundRoute routeInbound(String channelId, String channelName, Channel channel, String inboundDid) {
        String did = inboundDid != null ? inboundDid : ChannelNames.extractDid(channel);
        String callerNumber = ChannelNames.extractCallerNumber(channel);
        InboundDecision decision = inboundCallGate.decide(did, callerNumber);
        if (decision.route() != null && !ChannelNames.isTrunkAllowedFor(channelName, decision.route().companyId())) {
            log.warn("Inbound call {} to {} came in on trunk {}, which belongs to another company — hanging up",
                    channelId, did, ChannelNames.extractTrunk(channelName));
            ariClient.hangup(channelId);
            return null;
        }
        InboundRoute inboundRoute = null;
        switch (decision.kind()) {
            case NO_ROUTE -> {
                log.info("Inbound call {} to {}: no enabled route — hanging up", channelId, did);
                ariClient.hangup(channelId);
                return null;
            }
            case REJECT -> {
                rejectInbound(channelId, decision, callerNumber);
                return null;
            }
            case CLOSED -> {
                closeInbound(channelId, decision, callerNumber);
                return null;
            }
            case FLOW -> {
                flowCallService.startInboundFlow(channelId, channelName, channel, decision, did, callerNumber);
                return null;
            }
            case AGENT -> {
                inboundRoute = decision.route();
                if (decision.vip()) {
                    flowCallRegistry.markVip(channelId);
                }
            }
        }
        return inboundRoute;
    }

    /**
     * A caller rule refused the caller (docs/INBOUND-PBX-PLAN.md §9): a busy tone, a message
     * or a plain hang-up — and a call row with the reason, like every other attempt.
     */
    private void rejectInbound(String channelId, InboundDecision decision, String callerNumber) {
        InboundRoute route = decision.route();
        InboundCallerRule rule = decision.rule();
        long attemptId = callAttemptUseCase.startAttempt(route.companyId(), CallAttempt.NO_TARGET, channelId,
                callerNumber != null ? callerNumber : "ANONYMOUS", decision.language(), route.storedId(), null);
        callAttemptUseCase.recordError(attemptId,
                "refused by caller rule " + rule.phonePrefix() + " (" + rule.action() + ")");
        callEventUseCase.record(route.companyId(), attemptId, CallEventType.REJECTED,
                "action", rule.action().name(), "prefix", rule.phonePrefix());
        log.info("Inbound call {} from {} refused by caller rule {} ({})", channelId, PhoneMasker.mask(callerNumber),
                rule.phonePrefix(), rule.action());
        switch (rule.action()) {
            case BLOCK_BUSY -> ariClient.hangupBusy(channelId);
            case BLOCK_MESSAGE -> playMessageAndHangup(channelId, decision.message(), decision.language());
            default -> ariClient.hangup(channelId);
        }
        callAttemptUseCase.finishAttempt(attemptId, Disposition.REFUSED, null, 0);
    }

    /**
     * Outside the route's hours, or a route with nobody to answer: the closed message — the
     * route's own, or one built from its schedule ("ertaga soat to'qqizda ishni boshlaymiz")
     * — and a hang-up. An after-hours caller joins the missed-call list.
     */
    private void closeInbound(String channelId, InboundDecision decision, String callerNumber) {
        InboundRoute route = decision.route();
        long companyId = route.companyId();
        long attemptId = callAttemptUseCase.startAttempt(companyId, CallAttempt.NO_TARGET, channelId,
                callerNumber != null ? callerNumber : "INBOUND", decision.language(), route.storedId(), null);
        Instant started = Instant.now();
        log.info("Inbound call {} to route {}: {} — {}", channelId, route.id(),
                decision.afterHours() ? "outside business hours" : "nobody to answer",
                decision.message() != null ? "saying the closed message" : "hanging up");
        if (decision.message() != null) {
            callAttemptUseCase.markAnswered(attemptId);
            playMessageAndHangup(channelId, decision.message(), decision.language());
        } else {
            ariClient.hangup(channelId);
        }
        callAttemptUseCase.finishAttempt(attemptId, Disposition.NO_ANSWER, null,
                (int) Duration.between(started, Instant.now()).toSeconds());
        if (decision.afterHours() && callerNumber != null) {
            try {
                missedCallUseCase.record(companyId, new MissedCallOccurrence(callerNumber, MissedCallReason.AFTER_HOURS,
                        null, route.storedId(), attemptId != 0 ? attemptId : null, null));
            } catch (Exception e) {
                log.warn("[{}] missed call not recorded: {}", channelId, e.getMessage());
            }
        }
    }

    /** Says {@code text} (when there is one) to a caller not answered yet, then hangs up. */
    private void playMessageAndHangup(String channelId, String text, String language) {
        try {
            if (text != null && !text.isBlank() && ttsProperties.enabled()) {
                // Synthesis first, answer second: the caller hears ringback while the message
                // is rendered instead of an open line playing nothing.
                short[] pcm = ttsRouter.synthesize(text,
                        language != null ? language : ttsProperties.defaultLanguage(), null);
                ariClient.answer(channelId);
                callMediaFactory.playToChannel(channelId, pcm);
            }
        } catch (Exception e) {
            log.warn("Could not say the closing message to {}: {}", channelId, e.getMessage());
        } finally {
            ariClient.hangup(channelId);
        }
    }
}
