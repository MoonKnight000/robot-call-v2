package uz.murodjon.robotcallv2.agent.dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects and calculates advanced analytics metrics for calls
 * (RETELL_AI_GAP_ANALYSIS.md #4).
 *
 * <p>Tracks: sentiment trends, drop-off analysis, containment rate,
 * success rate, CSAT, average handling time (AHT), first call resolution rate.
 */
@Component
public class AnalyticsMetricsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsMetricsService.class);

    /** Metric types tracked. */
    public enum MetricType {
        SENTIMENT_TREND,
        DROP_OFF_STAGE,
        CONTAINMENT_RATE,
        SUCCESS_RATE,
        CSAT_SCORE,
        AVERAGE_HANDLING_TIME,
        FIRST_CALL_RESOLUTION,
        FRUSTRATION_RATE,
        TRANSFER_RATE
    }

    // In-memory metrics storage (for a production system, this would use a database)
    private final Map<String, AtomicInteger> metricCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicDouble> metricSums = new ConcurrentHashMap<>();

    /**
     * Record a call completion with its metrics.
     *
     * @param companyId     the company ID
     * @param channelId     the call channel ID
     * @param durationSec   call duration in seconds
     * @param sentiment     final sentiment
     * @param contained     whether AI resolved the issue without transfer
     * @param transferred   whether the call was transferred to an operator
     * @param success       whether the call achieved its goal
     * @param csatScore     customer satisfaction score (1-5), null if not surveyed
     * @param dropOffStage  stage where caller disconnected, null if completed
     */
    public void recordCallCompletion(long companyId, String channelId,
                                     int durationSec, CustomerSentiment sentiment,
                                     boolean contained, boolean transferred,
                                     boolean success, Integer csatScore,
                                     String dropOffStage) {
        String companyKey = "company:" + companyId;

        // Containment rate
        incrementCounter(companyKey + ":calls_total");
        if (contained) {
            incrementCounter(companyKey + ":calls_contained");
        }
        if (transferred) {
            incrementCounter(companyKey + ":calls_transferred");
        }
        if (success) {
            incrementCounter(companyKey + ":calls_success");
        }
        if (sentiment == CustomerSentiment.FRUSTRATED) {
            incrementCounter(companyKey + ":calls_frustrated");
        }

        // AHT
        addSum(companyKey + ":total_duration", durationSec);
        incrementCounter(companyKey + ":duration_count");

        // CSAT
        if (csatScore != null) {
            addSum(companyKey + ":csat_total", csatScore);
            incrementCounter(companyKey + ":csat_count");
        }

        // Drop-off
        if (dropOffStage != null) {
            incrementCounter(companyKey + ":dropoff:" + dropOffStage);
        }

        log.info("Analytics recorded for call {}: duration={}s, sentiment={}, contained={}, transferred={}",
                channelId, durationSec, sentiment, contained, transferred);
    }

    /**
     * Get analytics summary for a company.
     *
     * @param companyId the company ID
     * @return map of metric names to their values
     */
    public Map<String, Object> getAnalyticsSummary(long companyId) {
        String companyKey = "company:" + companyId;
        Map<String, Object> summary = new LinkedHashMap<>();

        long totalCalls = getCounter(companyKey + ":calls_total");
        long contained = getCounter(companyKey + ":calls_contained");
        long transferred = getCounter(companyKey + ":calls_transferred");
        long success = getCounter(companyKey + ":calls_success");
        long frustrated = getCounter(companyKey + ":calls_frustrated");

        summary.put("totalCalls", totalCalls);
        summary.put("containedCalls", contained);
        summary.put("transferredCalls", transferred);
        summary.put("successfulCalls", success);
        summary.put("frustratedCalls", frustrated);

        if (totalCalls > 0) {
            summary.put("containmentRate", (double) contained / totalCalls);
            summary.put("transferRate", (double) transferred / totalCalls);
            summary.put("successRate", (double) success / totalCalls);
            summary.put("frustrationRate", (double) frustrated / totalCalls);
        }

        long durationCount = getCounter(companyKey + ":duration_count");
        if (durationCount > 0) {
            double totalDuration = getSum(companyKey + ":total_duration");
            summary.put("averageHandlingTime", totalDuration / durationCount);
        }

        long csatCount = getCounter(companyKey + ":csat_count");
        if (csatCount > 0) {
            double csatTotal = getSum(companyKey + ":csat_total");
            summary.put("csatScore", csatTotal / csatCount);
        }

        return summary;
    }

    private void incrementCounter(String key) {
        metricCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
    }

    private int getCounter(String key) {
        AtomicInteger counter = metricCounters.get(key);
        return counter != null ? counter.get() : 0;
    }

    private void addSum(String key, double value) {
        AtomicDouble sum = metricSums.computeIfAbsent(key, k -> new AtomicDouble(0));
        sum.addAndGet(value);
    }

    private double getSum(String key) {
        AtomicDouble sum = metricSums.get(key);
        return sum != null ? sum.get() : 0.0;
    }

    /** Simple atomic double for thread-safe accumulation. */
    private static class AtomicDouble {
        private volatile long storedBits = 0;

        AtomicDouble(double value) {
            storedBits = Double.doubleToLongBits(value);
        }

        void addAndGet(double delta) {
            long current, next;
            do {
                current = storedBits;
                next = Double.doubleToLongBits(Double.longBitsToDouble(current) + delta);
            } while (!java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater(
                    AtomicDouble.class, "storedBits").compareAndSet(this, current, next));
        }

        double get() {
            return Double.longBitsToDouble(storedBits);
        }
    }
}
