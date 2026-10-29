package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Real-time intent detection — classifies what the caller wants to do
 * (RETELL_AI_GAP_ANALYSIS.md #6).
 *
 * <p>Intents are detected from the caller's last utterance and can be used to
 * route the conversation to the appropriate scenario stage or trigger actions.
 * Common intents: INQUIRY, COMPLAINT, ORDER, CANCELLATION, PAYMENT, SUPPORT,
 * TRANSFER_REQUEST, CHAT, UNKNOWN.
 */
@Component
public class IntentDetector {

    private static final Logger log = LoggerFactory.getLogger(IntentDetector.class);
    private static final long TIMEOUT_MS = 3000;
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final ChatClient chatClient;
    private final boolean enabled;

    public IntentDetector(ObjectProvider<ChatClient> chatClientProvider, IntentProperties properties) {
        this.enabled = properties != null && properties.enabled();
        ChatClient client = chatClientProvider.getIfAvailable();
        this.chatClient = client;
        if (!enabled) {
            log.info("Intent detection disabled (voice-agent.intent.enabled=false)");
        } else if (client == null) {
            log.warn("Intent detection enabled but no ChatClient available");
        }
    }

    /**
     * Detect the caller's intent from their last utterance.
     *
     * @param utterance the caller's last spoken text
     * @param context   recent conversation context (last few turns), may be null
     * @return the detected intent and confidence; null if detection is unavailable
     */
    public DetectedIntent detect(String utterance, String context) {
        if (!enabled || chatClient == null || utterance == null || utterance.isBlank()) {
            return null;
        }
        try {
            String systemPrompt = """
                    You are an intent classifier for a voice call system.
                    Classify the caller's intent into one of these categories:
                    INQUIRY, COMPLAINT, ORDER, CANCELLATION, PAYMENT, SUPPORT, TRANSFER_REQUEST, CHAT, UNKNOWN
                    
                    Respond with JSON: {"intent": "CATEGORY", "confidence": 0.0-1.0}
                    Confidence reflects how certain you are about the classification.
                    """;

            String userPrompt = (context != null && !context.isBlank())
                    ? "Caller said: \"" + utterance + "\"\n\nRecent context:\n" + context
                    : "Caller said: \"" + utterance + "\"";

            CompletableFuture<ResponseEntity<ChatResponse, Map<String, Object>>> pending =
                    CompletableFuture.supplyAsync(() -> chatClient.prompt()
                            .system(systemPrompt)
                            .user(userPrompt)
                            .call()
                            .responseEntity(new MapOutputConverter()), EXECUTOR);

            ResponseEntity<ChatResponse, Map<String, Object>> answer;
            try {
                answer = pending.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                pending.cancel(true);
                log.warn("Intent detection timed out");
                return null;
            } catch (ExecutionException e) {
                log.warn("Intent detection failed: {}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                return null;
            }

            Map<String, Object> result = answer.entity();
            if (result == null) {
                return null;
            }

            String intentStr = (String) result.get("intent");
            Double confidence = result.get("confidence") instanceof Number n
                    ? n.doubleValue() : 0.5;

            Intent intent;
            try {
                intent = Intent.valueOf(intentStr != null ? intentStr.toUpperCase() : "UNKNOWN");
            } catch (IllegalArgumentException e) {
                intent = Intent.UNKNOWN;
            }

            return new DetectedIntent(intent, confidence);
        } catch (Exception e) {
            log.warn("Intent detection failed: {}", e.getMessage());
            return null;
        }
    }

    /** Detects an intent without holding up the caller's turn or the provider's network thread. */
    public void detectAsync(String utterance, String context, Consumer<DetectedIntent> onResult) {
        if (!enabled || chatClient == null || utterance == null || utterance.isBlank() || onResult == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            DetectedIntent result = detect(utterance, context);
            if (result != null) {
                onResult.accept(result);
            }
        }, EXECUTOR).exceptionally(failure -> {
            log.warn("Async intent detection failed: {}", failure.getMessage());
            return null;
        });
    }
}
