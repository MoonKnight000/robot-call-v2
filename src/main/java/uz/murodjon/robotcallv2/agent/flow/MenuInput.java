package uz.murodjon.robotcallv2.agent.flow;

import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowNode;

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * One menu node waiting for the caller's choice: a key — or, where extensions may be
 * dialled, a few — and, when the menu has keywords and speech recognition is up, a spoken
 * option. Opened by {@link FlowMenuReader}; closing it takes the recognizer off the line.
 */
final class MenuInput implements Closeable {

    private static final long INTER_DIGIT_MS = 1500;

    private final FlowCall call;
    private final CallFlowNode node;
    /** Listening for spoken options; null when the menu takes keys only. */
    private final SttStreamBridge bridge;
    private final BlockingQueue<String> phrases;

    MenuInput(FlowCall call, CallFlowNode node, SttStreamBridge bridge, BlockingQueue<String> phrases) {
        this.call = call;
        this.node = node;
        this.bridge = bridge;
        this.phrases = phrases;
    }

    /** The key(s) pressed or the option spoken within {@code timeoutMs}; empty on silence. */
    String readEntry(long timeoutMs) throws InterruptedException {
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
            if (bridge != null) {
                String spoken = phrases.poll();
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

    @Override
    public void close() {
        if (bridge != null) {
            call.detachListener(bridge);
            bridge.close();
        }
    }

    /** The option whose keyword the caller said, or null. */
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
}
