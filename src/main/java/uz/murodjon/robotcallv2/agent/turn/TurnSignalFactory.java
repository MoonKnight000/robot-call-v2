package uz.murodjon.robotcallv2.agent.turn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Opens a call's {@link TurnSignal}s from the turn settings: Smart Turn for a language it was
 * trained in, the text model otherwise, and Smart Turn in shadow for a language it is only being
 * evaluated in.
 */
@Component
public class TurnSignalFactory {

    private static final Logger log = LoggerFactory.getLogger(TurnSignalFactory.class);

    private final SmartTurnProperties smartTurnProperties;
    private final ObjectProvider<SmartTurnDetector> smartTurnDetectorProvider;
    private final TextTurnProperties textTurnProperties;
    private final ObjectProvider<TextTurnDetector> textTurnDetectorProvider;

    public TurnSignalFactory(SmartTurnProperties smartTurnProperties,
                             ObjectProvider<SmartTurnDetector> smartTurnDetectorProvider,
                             TextTurnProperties textTurnProperties,
                             ObjectProvider<TextTurnDetector> textTurnDetectorProvider) {
        this.smartTurnProperties = smartTurnProperties;
        this.smartTurnDetectorProvider = smartTurnDetectorProvider;
        this.textTurnProperties = textTurnProperties;
        this.textTurnDetectorProvider = textTurnDetectorProvider;
    }

    /** The signal that decides this call's pauses, or null when none is on for its language. */
    public ActiveTurnSignal openActive(String language) {
        SmartTurnDetector smartTurn = findSmartTurn();
        if (smartTurn != null) {
            if (smartTurnProperties.supports(language)) {
                return new ActiveTurnSignal(new SmartTurnSignal(smartTurn, smartTurnProperties.threshold(),
                        smartTurnProperties.earlyThreshold()),
                        smartTurnProperties.maxExtendMs(), smartTurnProperties.earlyWaitMs());
            }
            log.debug("Smart Turn skipped for {} — not a language the model was trained for", language);
        }
        TextTurnDetector textTurn = textTurnProperties.enabled() ? textTurnDetectorProvider.getIfAvailable() : null;
        if (textTurn != null && textTurn.available()) {
            if (textTurnProperties.supports(language)) {
                return new ActiveTurnSignal(new TextTurnSignal(textTurn, textTurnProperties.threshold(),
                        textTurnProperties.earlyThreshold()),
                        textTurnProperties.maxExtendMs(), textTurnProperties.earlyWaitMs());
            }
            log.debug("Text turn skipped for {} — not a language the model is trusted for", language);
        }
        return null;
    }

    /**
     * Smart Turn for a language it is only being evaluated in ({@link SmartTurnProperties#shadowLanguages()}):
     * its score is written down next to what the caller did, never acted on. Null when not shadowed.
     */
    public TurnSignal openShadow(String language) {
        SmartTurnDetector smartTurn = findSmartTurn();
        if (smartTurn == null || !smartTurnProperties.shadows(language)) {
            return null;
        }
        return new SmartTurnSignal(smartTurn, smartTurnProperties.threshold(), smartTurnProperties.earlyThreshold());
    }

    private SmartTurnDetector findSmartTurn() {
        if (!smartTurnProperties.enabled()) {
            return null;
        }
        SmartTurnDetector detector = smartTurnDetectorProvider.getIfAvailable();
        return detector != null && detector.available() ? detector : null;
    }
}
