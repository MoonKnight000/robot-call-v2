package uz.murodjon.uysotvoice.agent.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.callrecord.repository.CallAttemptJpaRepository;
import uz.murodjon.uysotvoice.company.config.CompanyProperties;
import uz.murodjon.uysotvoice.notification.enums.NotificationType;
import uz.murodjon.uysotvoice.notification.service.NotificationService;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically checks the recent call success rate and logs an alert when it drops
 * below a threshold (PROJECT.md §10 Bosqich 12). "Success" = a promise to pay.
 * Log-based by design — wire the WARN log to your alerting sink (e.g. Loki/Alertmanager).
 * Best-effort: a DB error is logged and skipped.
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final CallAttemptJpaRepository callAttempts;
    private final NotificationService notificationService;
    private final CompanyProperties companyProps;
    private final boolean enabled;
    private final int windowMinutes;
    private final int minSample;
    private final double threshold;
// todo buni propertyga olish kerak buncha yamlda oqildigan fieldlarni
    public AlertingService(CallAttemptJpaRepository callAttempts,
                           NotificationService notificationService,
                           CompanyProperties companyProps,
                           @Value("${voice-agent.alerting.enabled:true}") boolean enabled,
                           @Value("${voice-agent.alerting.window-minutes:30}") int windowMinutes,
                           @Value("${voice-agent.alerting.min-sample:20}") int minSample,
                           @Value("${voice-agent.alerting.success-threshold:0.3}") double threshold) {
        this.callAttempts = callAttempts;
        this.notificationService = notificationService;
        this.companyProps = companyProps;
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
}
