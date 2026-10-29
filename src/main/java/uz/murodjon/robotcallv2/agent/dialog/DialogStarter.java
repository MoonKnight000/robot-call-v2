package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.RealtimeMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;

import java.util.function.Consumer;

/**
 * Decides which pipeline a call talks on and starts the realtime one with its way back to
 * the cascade — the part every place that hands a call to an agent (a dialled or inbound
 * call, an agent node in a call flow) does the same way.
 *
 * <p>What differs between those places stays with them: when the audio listeners are put
 * on the line, and what happens when the cascade cannot take over either.
 */
@Component
public class DialogStarter {

    private static final Logger log = LoggerFactory.getLogger(DialogStarter.class);

    private final RealtimeDialogEngine realtimeDialogEngine;
    private final RealtimeMetrics realtimeMetrics;

    public DialogStarter(RealtimeDialogEngine realtimeDialogEngine, RealtimeMetrics realtimeMetrics) {
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.realtimeMetrics = realtimeMetrics;
    }

    /**
     * The pipeline this call runs on: an A/B variant testing the pipeline wins over the
     * agent's own choice for exactly the calls assigned to it, and a call with neither runs
     * on the cascade. A realtime mode with no engine available runs on the cascade too.
     *
     * @param override the A/B variant's pipeline, or null
     */
    public PipelineMode resolveMode(String channelId, AiAgent agent, PipelineMode override) {
        PipelineMode mode = override != null ? override
                : agent != null && agent.speechEngine().mode() != null ? agent.speechEngine().mode()
                : PipelineMode.CASCADE;
        if (mode.usesRealtimeEngine() && !realtimeDialogEngine.available()) {
            log.warn("[{}] agent is set to {} but no engine is available — running cascade", channelId, mode);
            String engine = agent != null && agent.speechEngine() != null
                    ? agent.speechEngine().realtimeProvider() : null;
            realtimeMetrics.realtimeFallback(engine);
            return PipelineMode.CASCADE;
        }
        return mode;
    }

    /**
     * Starts the realtime engine on {@code start}'s call. Should the engine drop mid-call and
     * not recover, {@code continueOnCascade} gets the same call back with the agent
     * apologising for the dropped line ({@link CallStart#withReconnectingAgent}) — the
     * caller is still on the line, so the cascade carries on in another voice.
     *
     * @param mode {@link PipelineMode#REALTIME} or {@link PipelineMode#HALF_CASCADE}
     * @return false when no engine could be opened — nothing has been said yet, so the
     *         caller of this method runs the call on the cascade its own way
     */
    public boolean startRealtime(CallStart start, PipelineMode mode, RealtimeAudioBridge bridge,
                                 Consumer<CallStart> continueOnCascade) {
        return realtimeDialogEngine.startCall(start, bridge,
                () -> continueOnCascade.accept(start.withReconnectingAgent()),
                mode == PipelineMode.HALF_CASCADE);
    }
}
