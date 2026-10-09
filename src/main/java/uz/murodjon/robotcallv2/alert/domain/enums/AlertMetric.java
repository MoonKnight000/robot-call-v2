package uz.murodjon.robotcallv2.alert.domain.enums;

/**
 * What an {@code AlertRule} watches, over the AI agent calls that ended in its window.
 * A rate is a percentage, 0–100.
 */
public enum AlertMetric {
    /** Conversions (a promise to pay, a completed scenario, a reached goal) among ended calls. */
    SUCCESS_RATE(true, true),
    /** Calls handed to a person among ended calls. */
    TRANSFER_RATE(true, true),
    /** Technical failures and carrier rejections among ended calls. */
    FAILED_CALL_RATE(true, true),
    /** Calls whose analysis read the caller as negative or hostile, among analysed calls. */
    NEGATIVE_SENTIMENT_RATE(true, true),
    /** The average time between the caller finishing and the agent answering, in ms. */
    AVG_TURN_LATENCY_MS(false, true),
    /** Company tool requests that did not come back 2xx. */
    TOOL_ERROR_COUNT(false, false),
    /** Ended calls — below a threshold means the dialer or the trunk has gone quiet. */
    CALL_COUNT(false, false);

    private final boolean percentage;
    private final boolean sampleGated;

    AlertMetric(boolean percentage, boolean sampleGated) {
        this.percentage = percentage;
        this.sampleGated = sampleGated;
    }

    /** A rate in percent, so its threshold cannot exceed 100. */
    public boolean isPercentage() {
        return percentage;
    }

    /** Judged only once the window holds at least the rule's {@code minCalls}: three calls say nothing. */
    public boolean isSampleGated() {
        return sampleGated;
    }
}
