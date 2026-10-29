package uz.murodjon.robotcallv2.agent.dialog;

/**
 * Result of caller intent detection.
 *
 * @param intent     the detected intent category
 * @param confidence classification confidence (0.0 to 1.0)
 */
public record DetectedIntent(Intent intent, double confidence) {

    /** Returns true if the intent is detected with reasonable confidence. */
    public boolean isReliable() {
        return confidence >= 0.6 && intent != Intent.UNKNOWN;
    }
}
