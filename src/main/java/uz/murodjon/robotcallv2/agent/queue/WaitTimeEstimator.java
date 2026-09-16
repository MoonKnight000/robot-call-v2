package uz.murodjon.robotcallv2.agent.queue;

/**
 * The expected wait a queue announces (docs/INBOUND-PBX-PLAN.md §7.3):
 * position × average handle time ÷ operators taking calls, rounded up to a minute.
 *
 * <p>Said only when it can be believed. With a handful of calls behind the average, or one
 * operator whose next call might take thirty seconds or thirty minutes, the number is noise
 * — and a promised "two minutes" that turns into ten costs more goodwill than silence.
 */
public final class WaitTimeEstimator {

    static final int MIN_SAMPLES = 20;
    static final int MIN_OPERATORS = 2;

    private WaitTimeEstimator() {
    }

    /**
     * @param averageHandleSeconds recent average of how long an operator spends per call
     * @param samples              how many calls that average is over
     * @param activeOperators      operators signed in and not on a break
     * @return whole minutes, at least 1; null when the estimate would not be reliable
     */
    public static Integer estimateMinutes(int position, double averageHandleSeconds, int samples, int activeOperators) {
        if (samples < MIN_SAMPLES || activeOperators < MIN_OPERATORS || averageHandleSeconds <= 0 || position < 1) {
            return null;
        }
        double seconds = position * averageHandleSeconds / activeOperators;
        return (int) Math.max(1, Math.ceil(seconds / 60.0));
    }
}
