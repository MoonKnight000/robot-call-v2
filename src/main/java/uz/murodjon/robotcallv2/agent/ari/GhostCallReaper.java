package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.DialogOutcome;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.session.CallSession;

import java.time.Clock;
import java.time.Instant;

/**
 * Tears down calls Asterisk lost track of: a session still here long after its limit, with
 * no StasisEnd ever arriving for it.
 */
@Component
class GhostCallReaper {

    private static final Logger log = LoggerFactory.getLogger(GhostCallReaper.class);

    /**
     * The ghost reaper's limit for a call people are having with each other — in a queue, with
     * an operator, a callback. The AI's own call-length limit does not apply to them; this only
     * catches a channel Asterisk lost.
     */
    private static final long HUMAN_CALL_CEILING_SECONDS = 4 * 3600;

    private final CallSessionRegistry callSessionRegistry;
    private final FlowCallRegistry flowCallRegistry;
    private final PbxLegService pbxLegService;
    private final CallTeardownService callTeardownService;
    private final DialogRouter dialogRouter;
    private final DialogProperties dialogProperties;
    private final AsteriskProperties asteriskProperties;
    private final Clock clock;
    private final AriClient ariClient;

    GhostCallReaper(CallSessionRegistry callSessionRegistry,
                    FlowCallRegistry flowCallRegistry,
                    PbxLegService pbxLegService,
                    CallTeardownService callTeardownService,
                    DialogRouter dialogRouter,
                    DialogProperties dialogProperties,
                    AsteriskProperties asteriskProperties,
                    Clock clock,
                    AriClient ariClient) {
        this.callSessionRegistry = callSessionRegistry;
        this.flowCallRegistry = flowCallRegistry;
        this.pbxLegService = pbxLegService;
        this.callTeardownService = callTeardownService;
        this.dialogRouter = dialogRouter;
        this.dialogProperties = dialogProperties;
        this.asteriskProperties = asteriskProperties;
        this.clock = clock;
        this.ariClient = ariClient;
    }

    @Scheduled(fixedDelay = 30_000)
    public void reapGhostCalls() {
        if (callSessionRegistry.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        int maxCallSeconds = dialogProperties != null && dialogProperties.maxCallSeconds() > 0
                ? dialogProperties.maxCallSeconds()
                : asteriskProperties.ghostCallFallbackSeconds();
        int ghostTimeoutSeconds = maxCallSeconds + asteriskProperties.ghostCallGraceSeconds();

        for (CallSession session : callSessionRegistry.findAll()) {
            if (session.startedAt() != null) {
                long elapsed = java.time.Duration.between(session.startedAt(), now).getSeconds();
                long limit = flowCallRegistry.contains(session.channelId()) || pbxLegService.isLinked(session.channelId())
                        || callSessionRegistry.isOperatorJoined(session.channelId())
                        ? HUMAN_CALL_CEILING_SECONDS : ghostTimeoutSeconds;
                if (elapsed > limit) {
                    log.error("[{}] GHOST CALL DETECTED: call active for {}s (limit {}s) without StasisEnd. Forcing teardown.",
                            session.channelId(), elapsed, limit);
                    try {
                        ariClient.hangup(session.channelId());
                    } catch (Exception e) {
                        log.debug("[{}] Ghost call hangup attempt failed: {}", session.channelId(), e.getMessage());
                    }
                    DialogOutcome outcome = dialogRouter.outcome(session.channelId());
                    DialogTechnicalSnapshot technical = dialogRouter.technicalSnapshot(session.channelId());
                    dialogRouter.endCall(session.channelId());
                    callTeardownService.tearDownIfClaimed(session.channelId(), outcome,
                            callSessionRegistry.findHangupCause(session.channelId()), technical, true);
                }
            }
        }
    }
}
