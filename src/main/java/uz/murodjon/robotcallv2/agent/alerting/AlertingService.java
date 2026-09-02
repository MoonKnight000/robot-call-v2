package uz.murodjon.robotcallv2.agent.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically checks two things a degrading deployment shows up in first: the recent
 * call success rate ("success" = a promise to pay) and the §1.3 turnaround budget.
 * Both are logged as an alert when they cross their threshold (PROJECT.md §10 Bosqich 12).
 * Log-based by design — wire the WARN log to your alerting sink (e.g. Loki/Alertmanager).
 * Best-effort: a DB or metric error is logged and skipped.
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final CallAttemptJpaRepository callAttempts;
    private final NotificationService notificationService;
    private final CompanyProperties companyProps;
    private final VoiceMetrics metrics;
    private final boolean enabled;
    private final int windowMinutes;
    private final int minSample;
    private final double threshold;
    private final int turnaroundBudgetMs;
    private final int turnaroundMinTurns;

    /** Turns already counted when {@link #checkTurnaround} last ran — the window is the delta. */
    private long lastTurnaroundCount;
// todo buni propertyga olish kerak buncha yamlda oqildigan fieldlarni
    public AlertingService(CallAttemptJpaRepository callAttempts,
                           NotificationService notificationService,
                           CompanyProperties companyProps,
                           VoiceMetrics metrics,
                           @Value("${voice-agent.alerting.enabled:true}") boolean enabled,
                           @Value("${voice-agent.alerting.window-minutes:30}") int windowMinutes,
                           @Value("${voice-agent.alerting.min-sample:20}") int minSample,
                           @Value("${voice-agent.alerting.success-threshold:0.3}") double threshold,
                           @Value("${voice-agent.alerting.turnaround-budget-ms:1000}") int turnaroundBudgetMs,
                           @Value("${voice-agent.alerting.turnaround-min-turns:30}") int turnaroundMinTurns) {
        this.callAttempts = callAttempts;
        this.notificationService = notificationService;
        this.companyProps = companyProps;
        this.metrics = metrics;
        this.enabled = enabled;
        this.windowMinutes = windowMinutes;
        this.minSample = minSample;
        this.threshold = threshold;
        this.turnaroundBudgetMs = turnaroundBudgetMs;
        this.turnaroundMinTurns = turnaroundMinTurns;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkSuccessRate() {
        if (!enabled) {
            return;
        }
        try {
            Instant since = Instant.now().minus(windowMinutes, ChronoUnit.MINUTES);
            long total = callAttempts.countByEndedAtGreaterThanEqual(since);
            if (total < minSample) {
                return; // not enough data to judge
            }
            long success = callAttempts.countByEndedAtGreaterThanEqualAndDisposition(since, Disposition.PROMISE_TO_PAY);
            double rate = success / (double) total;
            if (rate < threshold) {
                log.error("ALERT: call success rate {}% over last {}min ({}/{}) below threshold {}%",
                        Math.round(rate * 100), windowMinutes, success, total, Math.round(threshold * 100));
                // Not company-scoped (this check runs across every call, not per-tenant) —
                // notifies the default company same as the rest of this best-effort check.
                notificationService.notify(companyProps.defaultId(), NotificationType.ERROR_OCCURRED,
                        "Xato yuz berdi", "Qo'ng'iroq muvaffaqiyat darajasi " + Math.round(rate * 100)
                                + "% ga tushdi (oxirgi " + windowMinutes + " daqiqada)", null);
            } else {
                log.info("Success rate {}% over last {}min ({}/{})",
                        Math.round(rate * 100), windowMinutes, success, total);
            }
        } catch (Exception e) {
            log.warn("Success-rate check failed: {}", e.getMessage());
        }
    }

    /**
     * Watch the §1.3 turnaround budget: how long a caller waits between finishing their
     * sentence and hearing the bot. It is the number that decides whether the agent feels
     * like a conversation, and it degrades quietly — a slower LLM, a cold TTS cache, a
     * saturated RTP thread all show up here long before anyone complains, and none of
     * them show up in the success rate above.
     *
     * <p>The percentile is a rolling one, so the sample gate is the number of turns
     * measured since the previous run: a quiet window with three turns says nothing.
     */
    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkTurnaround() {
        if (!enabled) {
            return;
        }
        try {
            long count = metrics.turnaroundCount();
            long turns = count - lastTurnaroundCount;
            lastTurnaroundCount = count;
            if (turns < turnaroundMinTurns) {
                return;
            }
            long p95 = Math.round(metrics.turnaroundP95Millis());
            if (p95 > turnaroundBudgetMs) {
                log.error("ALERT: turnaround p95 {}ms over the last {} turns exceeds the {}ms budget (§1.3)",
                        p95, turns, turnaroundBudgetMs);
                notificationService.notify(companyProps.defaultId(), NotificationType.ERROR_OCCURRED,
                        "Javob kechikmoqda", "Bot javobining kechikishi (p95) " + p95
                                + " ms — belgilangan " + turnaroundBudgetMs + " ms dan yuqori", null);
            } else {
                log.info("Turnaround p95 {}ms over the last {} turns", p95, turns);
            }
        } catch (Exception e) {
            log.warn("Turnaround check failed: {}", e.getMessage());
        }
    }
}
