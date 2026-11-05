package uz.murodjon.robotcallv2.agent.stt;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Recognition streams opened while an outbound call is still ringing, kept for the bridge
 * of the call that answers ({@link SttPrewarmProperties}).
 *
 * <p>A stream is opened off the caller's thread, and an answer never waits for one: a call
 * that is answered before its stream is up opens its own, and the late one is closed as soon
 * as it finishes opening. A stream is handed over only when the parameters the answered call
 * asks for equal the ones it was opened with and the stream is still alive; every other
 * outcome closes it and the call carries on exactly as it did before this class existed.
 */
@Component
public class SttPrewarmRegistry {

    private static final Logger log = LoggerFactory.getLogger(SttPrewarmRegistry.class);

    private final SttPrewarmProperties properties;
    private final ExecutorService callExecutor;
    private final Map<String, CompletableFuture<PreopenedStt>> pending = new ConcurrentHashMap<>();

    SttPrewarmRegistry(SttPrewarmProperties properties, ExecutorService callExecutor) {
        this.properties = properties;
        this.callExecutor = callExecutor;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    /**
     * Open a stream for {@code channelId} in the background. Does nothing when prewarming is
     * off, the provider keeps no idle stream open, or the cap on waiting streams is reached.
     *
     * @param ringMs how long the line may ring; the stream is closed this long (plus the
     *               configured slack) after it was opened if nobody has taken it
     */
    public void prewarm(String channelId, SttProvider provider, SttOpenParams params, long ringMs) {
        if (!properties.enabled() || !provider.supportsPrewarm() || pending.size() >= properties.maxPending()) {
            return;
        }
        CompletableFuture<PreopenedStt> slot = new CompletableFuture<>();
        if (pending.putIfAbsent(channelId, slot) != null) {
            return;
        }
        callExecutor.execute(() -> {
            try {
                ForwardingTranscriptListener forwarder = new ForwardingTranscriptListener();
                SttSession session = provider.startStream(params.language(), params.alternativeLanguages(), forwarder,
                        params.externalEndpointing(), params.hints(), params.model());
                slot.complete(new PreopenedStt(session, params,
                        System.currentTimeMillis() + ringMs + properties.ttlSlackMs(), forwarder));
            } catch (Exception e) {
                slot.completeExceptionally(e);
                pending.remove(channelId, slot);
                log.debug("[{}] STT prewarm not opened: {}", channelId, e.getMessage());
            }
        });
    }

    /**
     * The stream opened for {@code channelId} if it is usable for a call asking for
     * {@code wanted}, else {@code null} (and the stream, if any, is closed).
     */
    public PreopenedStt take(String channelId, SttOpenParams wanted) {
        CompletableFuture<PreopenedStt> slot = pending.remove(channelId);
        if (slot == null) {
            return null;
        }
        if (!slot.isDone()) {
            // The answer beat the stream: the call opens its own, this one is dropped when it lands.
            slot.thenAccept(this::close);
            log.info("[{}] STT prewarm was not ready at answer time", channelId);
            return null;
        }
        PreopenedStt preopened = slot.getNow(null);
        if (preopened == null) {
            return null;
        }
        if (!preopened.params().equals(wanted)) {
            log.info("[{}] STT prewarm discarded: the call asked for other parameters", channelId);
            close(preopened);
            return null;
        }
        if (!preopened.session().isAlive()) {
            log.info("[{}] STT prewarm discarded: the stream closed while the line rang", channelId);
            close(preopened);
            return null;
        }
        log.info("[{}] STT prewarm adopted", channelId);
        return preopened;
    }

    /** Streams of calls that were never answered. */
    @Scheduled(fixedDelay = 5_000)
    void sweepExpired() {
        long now = System.currentTimeMillis();
        pending.forEach((channelId, slot) -> {
            PreopenedStt preopened = slot.getNow(null);
            if (preopened != null && preopened.expiresAtMs() <= now && pending.remove(channelId, slot)) {
                close(preopened);
            }
        });
    }

    @PreDestroy
    void shutdown() {
        pending.values().forEach(slot -> slot.thenAccept(this::close));
        pending.clear();
    }

    private void close(PreopenedStt preopened) {
        if (preopened == null) {
            return;
        }
        try {
            preopened.session().close();
        } catch (IOException | RuntimeException e) {
            log.debug("STT prewarm close failed: {}", e.getMessage());
        }
    }
}
