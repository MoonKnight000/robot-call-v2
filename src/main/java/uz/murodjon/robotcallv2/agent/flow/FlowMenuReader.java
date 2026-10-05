package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProvider;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Opens a menu node's {@link MenuInput}: keys always, and a recognizer listening for the
 * menu's keywords when it has any. A recognizer that cannot be had leaves the menu on keys
 * only rather than failing the call.
 */
@Component
public class FlowMenuReader {

    private static final Logger log = LoggerFactory.getLogger(FlowMenuReader.class);

    private final SttProviderSelector sttProviderSelector;
    private final SttProperties sttProperties;
    private final SttMetrics metrics;

    public FlowMenuReader(SttProviderSelector sttProviderSelector, SttProperties sttProperties, SttMetrics metrics) {
        this.sttProviderSelector = sttProviderSelector;
        this.sttProperties = sttProperties;
        this.metrics = metrics;
    }

    MenuInput open(FlowCall call, CallFlowNode node) {
        if (node.keywords().isEmpty() || !sttProperties.enabled()) {
            return new MenuInput(call, node, null, null);
        }
        try {
            SttProvider provider = sttProviderSelector.findForCall(null);
            if (provider == null) {
                return new MenuInput(call, node, null, null);
            }
            BlockingQueue<String> phrases = new LinkedBlockingQueue<>();
            SttStreamBridge bridge = new SttStreamBridge(provider, provider.sampleRate(), 8000, call.channelId(),
                    call.language(), List.of(), (text, isFinal, confidence) -> {
                        if (isFinal && text != null && !text.isBlank()) {
                            phrases.offer(text);
                        }
                    }, null, metrics, null, sttProperties.responseTimeoutMs(), null, List.of(),
                    sttProviderSelector, null, sttProperties.stream());
            call.attachListener(bridge);
            return new MenuInput(call, node, bridge, phrases);
        } catch (Exception e) {
            log.warn("[{}] spoken menu unavailable, keys only: {}", call.channelId(), e.getMessage());
            return new MenuInput(call, node, null, null);
        }
    }
}
