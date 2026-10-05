package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.models.StasisEnd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.*;
import uz.murodjon.robotcallv2.agent.flow.FlowCall;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.metrics.RtpMetrics;
import uz.murodjon.robotcallv2.agent.queue.CallQueueDispatcher;
import uz.murodjon.robotcallv2.agent.queue.WaitingCall;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.RtpQuality;
import uz.murodjon.robotcallv2.agent.rtp.RtpStats;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallFinalizationUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignTargetUseCase;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.DialerSlotUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.input.TrunkHealthUseCase;
import uz.murodjon.robotcallv2.donotcall.application.port.input.DoNotCallUseCase;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.missedcall.domain.enums.MissedCallReason;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

/**
 * The end of an answered call: {@code StasisEnd} decides the call's outcome, and the
 * teardown releases its media (Asterisk's legs, then ours, then the RTP port) and settles
 * it everywhere else — dialer slot, trunk health, do-not-call list, finalization, campaign
 * target and A/B conversion.
 */
@Service
class CallTeardownService {

    private static final Logger log = LoggerFactory.getLogger(CallTeardownService.class);

    /**
     * Inbound packet loss above which a finished call is called out in the log. G.711
     * carries a lost 20ms packet without much complaint; a few percent is where words
     * start going missing and the recognizer begins inventing them.
     */
    private static final double POOR_RTP_LOSS_PERCENT = 5.0;

    private final CallSessionRegistry callSessionRegistry;
    private final FlowCallRegistry flowCallRegistry;
    private final PendingCallRegistry pendingCallRegistry;
    private final DialogRouter dialogRouter;
    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;
    private final AriClient ariClient;
    private final RtpPortAllocator portAllocator;
    private final RtpProperties rtpProperties;
    private final CallMetrics callMetrics;
    private final RtpMetrics rtpMetrics;
    private final CallRouteRegistry routeRegistry;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final DialerSlotUseCase dialerSlotUseCase;
    private final TrunkHealthUseCase trunkHealthUseCase;
    private final DoNotCallUseCase doNotCallUseCase;
    private final CallFinalizationUseCase callFinalizationUseCase;
    private final CampaignTargetUseCase campaignTargetUseCase;
    /** A/B counters for the campaign variant an outbound call was assigned (§ campaign variants). */
    private final CampaignVariantUseCase campaignVariantUseCase;
    private final CallQueueDispatcher callQueueDispatcher;
    private final ExecutorService callExecutor;

    CallTeardownService(CallSessionRegistry callSessionRegistry,
                        FlowCallRegistry flowCallRegistry,
                        PendingCallRegistry pendingCallRegistry,
                        DialogRouter dialogRouter,
                        DialogEngine dialogEngine,
                        RealtimeDialogEngine realtimeDialogEngine,
                        AriClient ariClient,
                        RtpPortAllocator portAllocator,
                        RtpProperties rtpProperties,
                        CallMetrics callMetrics,
                        RtpMetrics rtpMetrics,
                        CallRouteRegistry routeRegistry,
                        OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                        DialerSlotUseCase dialerSlotUseCase,
                        TrunkHealthUseCase trunkHealthUseCase,
                        DoNotCallUseCase doNotCallUseCase,
                        CallFinalizationUseCase callFinalizationUseCase,
                        CampaignTargetUseCase campaignTargetUseCase,
                        CampaignVariantUseCase campaignVariantUseCase,
                        CallQueueDispatcher callQueueDispatcher,
                        ExecutorService callExecutor) {
        this.callSessionRegistry = callSessionRegistry;
        this.flowCallRegistry = flowCallRegistry;
        this.pendingCallRegistry = pendingCallRegistry;
        this.dialogRouter = dialogRouter;
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.ariClient = ariClient;
        this.portAllocator = portAllocator;
        this.rtpProperties = rtpProperties;
        this.callMetrics = callMetrics;
        this.rtpMetrics = rtpMetrics;
        this.routeRegistry = routeRegistry;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.dialerSlotUseCase = dialerSlotUseCase;
        this.trunkHealthUseCase = trunkHealthUseCase;
        this.doNotCallUseCase = doNotCallUseCase;
        this.callFinalizationUseCase = callFinalizationUseCase;
        this.campaignTargetUseCase = campaignTargetUseCase;
        this.campaignVariantUseCase = campaignVariantUseCase;
        this.callQueueDispatcher = callQueueDispatcher;
        this.callExecutor = callExecutor;
    }

    /** Runs on the channel's event lane. */
    void onStasisEnd(StasisEnd event) {
        String channelId = event.getChannel().getId();
        CallMdc.with(channelId, () -> endStasis(channelId));
    }

    /** Teardown proper, with the channel already in the MDC — {@link #onStasisEnd}. */
    private void endStasis(String channelId) {
        FlowCall flowCall = flowCallRegistry.remove(channelId);
        if (flowCall != null) {
            flowCall.markGone();
            if (flowCall.operatorChannelId() != null) {
                ariClient.hangup(flowCall.operatorChannelId());
            }
        }
        boolean engineOwned = dialogEngine.owns(channelId) || realtimeDialogEngine.owns(channelId);
        DialogOutcome outcome = dialogRouter.outcome(channelId);
        DialogTechnicalSnapshot technical = dialogRouter.technicalSnapshot(channelId);
        DialogOutcome agentOutcome = flowCallRegistry.takeOutcome(channelId);
        DialogTechnicalSnapshot agentTechnical = flowCallRegistry.takeTechnical(channelId);
        flowCallRegistry.forgetSnapshot(channelId);
        flowCallRegistry.takeTransferCallback(channelId);
        flowCallRegistry.forgetVip(channelId);
        if (!engineOwned) {
            // The agent was stopped earlier — a flow moved the call on — so what it decided was kept.
            if (agentOutcome != null) {
                outcome = agentOutcome;
            }
            if (agentTechnical != null) {
                technical = agentTechnical;
            }
        }
        if (flowCall != null && (outcome == null || outcome.disposition() == null || flowCall.operatorUserId() != null)) {
            outcome = new DialogOutcome(resolveFlowDisposition(flowCall), outcome != null ? outcome.doNotCallReason() : null);
        }
        dialogRouter.endCall(channelId);
        callSessionRegistry.forgetOperatorJoined(channelId);
        // A call still being set up has no session to claim yet: the request is left on its
        // phase, and the setup thread tears the call down as soon as it registers it. Without
        // this a caller who hung up during setup (a slow inbound CRM lookup) left a full
        // dialog running on a dead channel until the reaper.
        if (callSessionRegistry.requestEnd(channelId)) {
            log.info("StasisEnd: channel {} — still being set up, the setup tears it down", channelId);
            return;
        }
        CallSession session = callSessionRegistry.claim(channelId);
        if (session == null) {
            return;
        }
        Integer cause = callSessionRegistry.findHangupCause(channelId);
        log.info("StasisEnd: channel {} — tearing down media (cause {})",
                channelId, HangupCause.label(cause));
        tearDownClaimed(session, outcome, cause, technical, true);
    }

    /**
     * The one way a live call is torn down: claims it ({@link CallSessionRegistry#claim}) and,
     * when this caller is the one that got it, tears it down — on {@code callExecutor} when
     * {@code async}, else on this thread. Safe to call from any number of threads at once.
     *
     * @return whether this caller claimed the call
     */
    boolean tearDownIfClaimed(String channelId, DialogOutcome outcome, Integer cause,
                              DialogTechnicalSnapshot technical, boolean async) {
        CallSession session = callSessionRegistry.claim(channelId);
        if (session == null) {
            return false;
        }
        tearDownClaimed(session, outcome, cause, technical, async);
        return true;
    }

    /**
     * Tears down a call whose claim the caller already holds, then forgets the channel —
     * for callers that act between the claim and the teardown (StasisEnd logs, the shutdown
     * hangs the caller up).
     */
    void tearDownClaimed(CallSession session, DialogOutcome outcome, Integer cause,
                         DialogTechnicalSnapshot technical, boolean async) {
        String channelId = session.channelId();
        Runnable work = () -> CallMdc.with(channelId, () -> {
            try {
                teardown(session, outcome, cause, technical);
            } finally {
                callSessionRegistry.finishEnding(channelId);
            }
        });
        if (async) {
            callExecutor.execute(work);
        } else {
            work.run();
        }
    }

    private void teardown(CallSession session, DialogOutcome outcome, Integer cause, DialogTechnicalSnapshot technical) {
        Disposition disposition = outcome.disposition();
        if (disposition == null) {
            disposition = HangupCause.toDisposition(cause);
            if (disposition != null) {
                log.info("[{}] no dialog outcome; hangup cause {} -> {}",
                        session.channelId(), HangupCause.label(cause), disposition);
            }
        }
        // Asterisk's side first, then ours, then the port. Releasing the port while the
        // externalMedia leg was still up let the next call bind it and receive the dying
        // leg's RTP — the endpoint latches onto the first sender, so that caller never
        // heard the bot.
        ARI current = ariClient.findConnection();
        if (current != null) {
            try {
                current.channels().hangup(session.extMediaChannelId()).execute();
            } catch (Exception e) {
                log.debug("externalMedia channel {} already gone: {}", session.extMediaChannelId(), e.getMessage());
            }
            try {
                current.bridges().destroy(session.bridgeId()).execute();
            } catch (Exception e) {
                log.debug("Bridge {} already gone: {}", session.bridgeId(), e.getMessage());
            }
        }
        session.endpoint().close();
        RtpQuality mediaQuality = reportRtpQuality(session);
        portAllocator.release(session.rtpPort());
        log.info("Torn down call {}", session.channelId());

        callMetrics.activeDec();
        routeRegistry.unregister(session.channelId());

        pendingCallRegistry.forgetWebTestCall(session.channelId());
        OutboundCall outbound = outboundCallTrackingUseCase.remove(session.channelId());
        long clientId = 0L;
        if (outbound != null) {
            clientId = outbound.clientId() != null ? outbound.clientId() : 0L;
            dialerSlotUseCase.release(outbound.companyId(), outbound.campaignId());
            trunkHealthUseCase.recordOutcome(outbound.sipTrunkId(), disposition);
            if (disposition == Disposition.DO_NOT_CALL) {
                // Through the use case, which stores the number normalised: the port stored it
                // as dialled, so the same person dialled in another spelling was not on the list.
                try {
                    doNotCallUseCase.add(outbound.companyId(), outbound.phone(), outcome.doNotCallReason(), DoNotCallSource.CALL);
                } catch (ValidationException e) {
                    // A number the list refuses must not stop the teardown before finalization.
                    log.warn("[{}] do-not-call entry not recorded: {}", session.channelId(), e.getMessage());
                }
            }
        }

        if (session.callAttemptId() != 0) {
            // Finalization has the last word on the disposition: it runs the summary,
            // which can recognise an outcome the caller hung up before a tool could
            // record. Applying the target's outcome first rescheduled such a call.
            disposition = callFinalizationUseCase.finalizeCall(session.callAttemptId(), clientId, session.scenarioId(),
                    session.wavPath() != null ? Path.of(session.wavPath()) : null, session.startedAt(), disposition,
                    session.channelName(), session.trunk(), technical, session.agent(), outbound != null ? outbound.phone() : null,
                    mediaQuality);
        }
        if (outbound != null) {
            campaignTargetUseCase.applyOutcome(outbound.companyId(), outbound.targetId(), disposition);
            // After finalization, not before: the summary can recognise a promise the caller
            // hung up on before a tool recorded it, and that promise is a conversion.
            if (outbound.variantId() != null && disposition != null && disposition.isConversion()) {
                campaignVariantUseCase.recordConversion(outbound.variantId());
            }
        }
    }

    /** Report the call's inbound audio quality and hand it on for the "Texnik" tab. */
    private RtpQuality reportRtpQuality(CallSession session) {
        RtpStats stats = session.endpoint().stats();
        RtpQuality quality = stats.quality();
        long received = quality.packetsReceived();
        rtpMetrics.recordRtpQuality(received, stats.lostPackets(), stats.reorderedPackets(), stats.jitterMillis());
        if (received == 0) {
            log.error("ALERT: no inbound RTP at all on call {} — the caller was never heard. "
                            + "Check that RTP_LOCAL_IP ({}) is reachable from Asterisk (docs/NETWORK.md)",
                    session.channelId(), rtpProperties.localIp());
            return quality;
        }
        rtpMetrics.recordRtpMos(quality.mosEstimate());
        double mos = Math.round(quality.mosEstimate() * 10) / 10.0;
        double loss = stats.lossPercent();
        if (loss >= POOR_RTP_LOSS_PERCENT) {
            log.warn("[{}] poor inbound RTP: {}% loss ({} lost / {} received), jitter {}ms, {} out of order, MOS ~{}",
                    session.channelId(), Math.round(loss), stats.lostPackets(), received,
                    Math.round(stats.jitterMillis()), stats.reorderedPackets(), mos);
        } else {
            log.info("[{}] inbound RTP: {} packets, {}% loss, jitter {}ms, MOS ~{}",
                    session.channelId(), received, Math.round(loss), Math.round(stats.jitterMillis()), mos);
        }
        return quality;
    }

    /** How a flow call ended, when no agent decided it. */
    private Disposition resolveFlowDisposition(FlowCall call) {
        if (call.operatorUserId() != null) {
            return Disposition.TRANSFERRED;
        }
        if (call.voicemailMessageId() != null) {
            return Disposition.VOICEMAIL;
        }
        if (call.missedReason() == MissedCallReason.CALLBACK_REQUESTED) {
            return Disposition.CALLBACK_REQUESTED;
        }
        WaitingCall waiting = callQueueDispatcher.findByCallerChannel(call.channelId());
        if (call.missedReason() != null || (waiting != null && waiting.connectedAt() == null)) {
            return Disposition.NO_ANSWER;
        }
        return Disposition.COMPLETED;
    }
}
