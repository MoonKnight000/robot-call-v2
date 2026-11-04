package uz.murodjon.robotcallv2.agent.turn;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The {@link TurnPolicy} a call's speech gate combines its verdicts with, as
 * {@code voice-agent.turn-taking} says ({@link TurnTakingProperties}).
 */
@Component
public class TurnPolicyFactory {

    private final TurnTakingProperties turnTakingProperties;

    public TurnPolicyFactory(TurnTakingProperties turnTakingProperties) {
        this.turnTakingProperties = turnTakingProperties;
    }

    /** The policy for a call in {@code language}. */
    public TurnPolicy findForLanguage(String language) {
        return switch (turnTakingProperties.engine()) {
            case AUTO -> AutoTurnPolicy.INSTANCE;
            case LLM -> new PriorityTurnPolicy("llm", List.of(TurnSource.LLM));
            case TURN_MODEL -> new PriorityTurnPolicy("turn-model", List.of(TurnSource.TURN_MODEL));
            case WORDS -> new PriorityTurnPolicy("words", List.of(TurnSource.WORDS));
            case MIXED -> new PriorityTurnPolicy("mixed", findMixedOrder(language));
        };
    }

    private List<TurnSource> findMixedOrder(String language) {
        if (language != null) {
            for (Map.Entry<String, List<TurnSource>> entry : turnTakingProperties.mixedLanguages().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(language) && !entry.getValue().isEmpty()) {
                    return entry.getValue();
                }
            }
        }
        return turnTakingProperties.mixedOrder();
    }
}
