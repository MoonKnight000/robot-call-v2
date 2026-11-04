package uz.murodjon.robotcallv2.agent.vad;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the voice-activity model a call is scored by, as {@code voice-agent.vad.engine} says
 * ({@link VadEngine}). A model asked for but not loaded falls back to the one that is, with a
 * warning — a call is never left without VAD because of a setting.
 */
@Component
public class VadScorerFactory {

    private static final Logger log = LoggerFactory.getLogger(VadScorerFactory.class);

    private final ObjectProvider<SileroVad> sileroVadProvider;
    private final ObjectProvider<TenVad> tenVadProvider;
    private final VadProperties vadProperties;
    private final VadEngine engine;

    public VadScorerFactory(ObjectProvider<SileroVad> sileroVadProvider, ObjectProvider<TenVad> tenVadProvider,
                            VadProperties vadProperties,
                            @Value("${voice-agent.vad.engine:AUTO}") VadEngine engine) {
        this.sileroVadProvider = sileroVadProvider;
        this.tenVadProvider = tenVadProvider;
        this.vadProperties = vadProperties;
        this.engine = engine;
    }

    /** The call's scorer, or null when no model can score. */
    public VadScorer open(String channelId) {
        return switch (engine) {
            case SILERO -> firstOf(channelId, openSilero(), null);
            case TEN -> firstOf(channelId, openTen(), openSilero());
            case AUTO -> {
                VadScorer ten = openTen();
                yield ten != null ? ten : openSilero();
            }
            case MIXED -> openMixed(channelId);
        };
    }

    private VadScorer openMixed(String channelId) {
        List<VadScorer> scorers = new ArrayList<>(2);
        VadScorer silero = openSilero();
        if (silero != null) {
            scorers.add(silero);
        }
        VadScorer ten = openTen();
        if (ten != null) {
            scorers.add(ten);
        }
        if (scorers.size() < 2) {
            log.warn("[{}] voice-agent.vad.engine=MIXED needs Silero and TEN VAD loaded; only {} is — scoring with it alone",
                    channelId, scorers.isEmpty() ? "neither" : scorers.getFirst().engine());
            return scorers.isEmpty() ? null : scorers.getFirst();
        }
        return new MixedVadScorer(scorers, vadProperties.threshold(), vadProperties.listeningThreshold());
    }

    private VadScorer firstOf(String channelId, VadScorer wanted, VadScorer fallback) {
        if (wanted == null && fallback != null) {
            log.warn("[{}] voice-agent.vad.engine={} is not loaded — scoring with {}", channelId, engine, fallback.engine());
        }
        return wanted != null ? wanted : fallback;
    }

    private VadScorer openSilero() {
        SileroVad silero = sileroVadProvider.getIfAvailable();
        return silero != null && silero.available() ? silero.openStream() : null;
    }

    private VadScorer openTen() {
        TenVad ten = tenVadProvider.getIfAvailable();
        return ten != null && ten.available() ? ten.openStream() : null;
    }
}
