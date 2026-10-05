package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.AriWSHelper;
import ch.loway.oss.ari4java.generated.models.*;
import ch.loway.oss.ari4java.tools.AriConnectionEvent;
import ch.loway.oss.ari4java.tools.RestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opens the ARI event websocket, keeps it open, and hands every event to the component
 * that owns it — on the channel's own event lane ({@link AriEventLanes}), never on
 * ari4java's websocket reader thread.
 */
@Component
class AriEventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AriEventDispatcher.class);

    private final AsteriskProperties asteriskProperties;
    private final AriClient ariClient;
    private final AriEventLanes ariEventLanes;
    private final CallSetupService callSetupService;
    private final CallTeardownService callTeardownService;
    private final CallSessionRegistry callSessionRegistry;
    private final UnansweredCallSettler unansweredCallSettler;
    private final DtmfInputRouter dtmfInputRouter;

    AriEventDispatcher(AsteriskProperties asteriskProperties,
                       AriClient ariClient,
                       AriEventLanes ariEventLanes,
                       CallSetupService callSetupService,
                       CallTeardownService callTeardownService,
                       CallSessionRegistry callSessionRegistry,
                       UnansweredCallSettler unansweredCallSettler,
                       DtmfInputRouter dtmfInputRouter) {
        this.asteriskProperties = asteriskProperties;
        this.ariClient = ariClient;
        this.ariEventLanes = ariEventLanes;
        this.callSetupService = callSetupService;
        this.callTeardownService = callTeardownService;
        this.callSessionRegistry = callSessionRegistry;
        this.unansweredCallSettler = unansweredCallSettler;
        this.dtmfInputRouter = dtmfInputRouter;
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void connect() {
        if (!asteriskProperties.enabled()) {
            log.info("Asterisk ARI disabled (voice-agent.asterisk.enabled=false); not connecting");
            return;
        }
        try {
            ariClient.open(createEventHandler());
            log.info("Connected to Asterisk ARI at {} as app '{}'", asteriskProperties.ariUrl(), asteriskProperties.appName());
        } catch (Exception e) {
            log.error("Failed to connect to Asterisk ARI at {}: {}", asteriskProperties.ariUrl(), e.getMessage());
        }
    }

    /**
     * Rebuilds the ARI connection when there is none — Asterisk was down at startup, or
     * the websocket failed for good (ari4java retries on its own first, and only then
     * reports onFailure). Before this, one failed connect left the app deaf until restart:
     * no StasisStart, no StasisEnd, and originates failing with ARI_NOT_CONNECTED.
     */
    @Scheduled(fixedDelay = 15_000, initialDelay = 15_000)
    public void ensureConnected() {
        if (asteriskProperties.enabled() && !ariClient.isConnected()) {
            log.warn("ARI is not connected — trying again");
            connect();
        }
    }

    private AriWSHelper createEventHandler() {
        return new AriWSHelper() {
            @Override
            public void onStasisStart(StasisStart event) {
                ariEventLanes.execute(ChannelNames.extractChannelId(event.getChannel()),
                        () -> callSetupService.onStasisStart(event));
            }

            @Override
            public void onStasisEnd(StasisEnd event) {
                ariEventLanes.execute(ChannelNames.extractChannelId(event.getChannel()),
                        () -> callTeardownService.onStasisEnd(event));
            }

            @Override
            public void onChannelHangupRequest(ChannelHangupRequest event) {
                ariEventLanes.execute(ChannelNames.extractChannelId(event.getChannel()),
                        () -> callSessionRegistry.rememberHangupCause(event.getChannel(), event.getCause()));
            }

            @Override
            public void onChannelDestroyed(ChannelDestroyed event) {
                ariEventLanes.execute(ChannelNames.extractChannelId(event.getChannel()),
                        () -> unansweredCallSettler.onChannelDestroyed(event));
            }

            @Override
            public void onChannelDtmfReceived(ChannelDtmfReceived event) {
                ariEventLanes.execute(ChannelNames.extractChannelId(event.getChannel()),
                        () -> dtmfInputRouter.onDtmf(event));
            }

            @Override
            public void onConnectionEvent(AriConnectionEvent event) {
                if (event == AriConnectionEvent.WS_DISCONNECTED) {
                    log.warn("ARI websocket disconnected — ari4java is reconnecting");
                } else {
                    log.info("ARI websocket connected");
                }
            }

            @Override
            public void onFailure(RestException e) {
                // ari4java reports this once its own reconnect attempts are spent.
                log.error("ARI websocket failed: {} — rebuilding the connection", e.getMessage());
                ariClient.dropConnection();
            }
        };
    }
}
