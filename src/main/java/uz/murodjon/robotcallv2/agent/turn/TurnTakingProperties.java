package uz.murodjon.robotcallv2.agent.turn;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * How a call's end-of-turn verdicts are combined. Bound from {@code voice-agent.turn-taking.*}.
 *
 * @param engine         {@link TurnEngine}; null is {@link TurnEngine#AUTO}
 * @param mixedOrder     for {@link TurnEngine#MIXED}: the sources in the order they are trusted
 *                       ({@code llm}, {@code turn-model}, {@code words}); empty falls back to
 *                       llm, turn-model, words
 * @param mixedLanguages for {@link TurnEngine#MIXED}: an order per call language ({@code uz-UZ}),
 *                       in place of {@code mixedOrder}
 */
@ConfigurationProperties(prefix = "voice-agent.turn-taking")
public record TurnTakingProperties(
        TurnEngine engine,
        List<TurnSource> mixedOrder,
        Map<String, List<TurnSource>> mixedLanguages
) {

    public TurnTakingProperties {
        engine = engine == null ? TurnEngine.AUTO : engine;
        mixedOrder = mixedOrder == null || mixedOrder.isEmpty()
                ? List.of(TurnSource.LLM, TurnSource.TURN_MODEL, TurnSource.WORDS) : List.copyOf(mixedOrder);
        mixedLanguages = mixedLanguages == null ? Map.of() : Map.copyOf(mixedLanguages);
    }
}
