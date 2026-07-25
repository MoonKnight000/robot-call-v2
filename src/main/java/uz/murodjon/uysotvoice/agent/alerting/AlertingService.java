package uz.murodjon.uysotvoice.agent.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Periodically checks the recent call success rate and logs an alert when it drops
 * below a threshold (PROJECT.md §10 Bosqich 12). "Success" = a promise to pay.
 * Log-based by design — wire the WARN log to your alerting sink (e.g. Loki/Alertmanager).
 * Best-effort: a DB error is logged and skipped.
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int windowMinutes;
    private final int minSample;
    private final double threshold;

    public AlertingService(JdbcTemplate jdbc,
                           @Value("${voice-agent.alerting.enabled:true}") boolean enabled,
                           @Value("${voice-agent.alerting.window-minutes:30}") int windowMinutes,
                           @Value("${voice-agent.alerting.min-sample:20}") int minSample,
                           @Value("${voice-agent.alerting.success-threshold:0.3}") double threshold) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.windowMinutes = windowMinutes;
        this.minSample = minSample;
        this.threshold = threshold;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkSuccessRate() {
        if (!enabled) {
            return;
        }
        try {
            Long total = jdbc.queryForObject(
                    "SELECT count(*) FROM call_attempt WHERE ended_at >= now() - (? * interval '1 minute')",
                    Long.class, windowMinutes);
            if (total == null || total < minSample) {
                return; // not enough data to judge
            }
            Long success = jdbc.queryForObject(
                    "SELECT count(*) FROM call_attempt WHERE ended_at >= now() - (? * interval '1 minute') "
                            + "AND disposition = 'PROMISE_TO_PAY'",
                    Long.class, windowMinutes);
            double rate = (success != null ? success : 0) / (double) total;
            if (rate < threshold) {
                log.error("ALERT: call success rate {}% over last {}min ({}/{}) below threshold {}%",
                        Math.round(rate * 100), windowMinutes, success, total, Math.round(threshold * 100));
            } else {
                log.info("Success rate {}% over last {}min ({}/{})",
                        Math.round(rate * 100), windowMinutes, success, total);
            }
        } catch (Exception e) {
            log.warn("Success-rate check failed: {}", e.getMessage());
        }
    }
}
