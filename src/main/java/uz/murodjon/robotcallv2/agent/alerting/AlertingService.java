package uz.murodjon.robotcallv2.agent.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Periodically checks what a degrading deployment shows up in first: the recent
 * call success rate ("success" = a promise to pay), the §1.3 turnaround budget, and
 * whether the pipeline's own optimizations are still paying for themselves.
 * Each is logged as an alert when it crosses its threshold (PROJECT.md §10 Bosqich 12).
 * Log-based by design — wire the WARN log to your alerting sink (e.g. Loki/Alertmanager).
 * Best-effort: a DB or metric error is logged and skipped.
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final CallAttemptJpaRepository callAttemptJpaRepository;
    private final NotificationService notificationService;
    private final CompanyProperties companyProperties;
    private final VoiceMetrics voiceMetrics;
    private final AlertingProperties alertingProperties;

    /** Turns already counted when {@link #checkTurnaround} last ran — the window is the delta. */
    private long lastTurnaroundCount;

    /** Counters as {@link #checkEfficiency} last saw them; every ratio below is a delta. */
    private long lastSpeculationsStarted;
    private long lastSpeculationsHit;
    private long lastTtsCacheHits;
    private long lastTtsCacheMisses;
    private long lastPromptTokens;
    private long lastCachedTokens;

    public AlertingService(CallAttemptJpaRepository callAttemptJpaRepository,
                           NotificationService notificationService,
                           CompanyProperties companyProperties,
                           VoiceMetrics voiceMetrics,
                           AlertingProperties alertingProperties) {
        this.callAttemptJpaRepository = callAttemptJpaRepository;
        this.notificationService = notificationService;
        this.companyProperties = companyProperties;
        this.voiceMetrics = voiceMetrics;
        this.alertingProperties = alertingProperties;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkSuccessRate() {
        if (!alertingProperties.enabled()) {
            return;
        }
        try {
            Instant since = Instant.now().minus(alertingProperties.windowMinutes(), ChronoUnit.MINUTES);
            long total = callAttemptJpaRepository.countByEndedAtGreaterThanEqual(since);
            if (total < alertingProperties.minSample()) {
                return; // not enough data to judge
            }
            long success = callAttemptJpaRepository.countByEndedAtGreaterThanEqualAndDisposition(since, Disposition.PROMISE_TO_PAY);
            double rate = success / (double) total;
            if (rate < alertingProperties.successThreshold()) {
                log.error("ALERT: call success rate {}% over last {}min ({}/{}) below threshold {}%",
                        Math.round(rate * 100), alertingProperties.windowMinutes(), success, total, Math.round(alertingProperties.successThreshold() * 100));
                // Not company-scoped (this check runs across every call, not per-tenant) —
                // notifies the default company same as the rest of this best-effort check.
                notificationService.notify(companyProperties.defaultId(), NotificationType.ERROR_OCCURRED,
                        "Xato yuz berdi", "Qo'ng'iroq muvaffaqiyat darajasi " + Math.round(rate * 100)
                                + "% ga tushdi (oxirgi " + alertingProperties.windowMinutes() + " daqiqada)", null);
            } else {
                log.info("Success rate {}% over last {}min ({}/{})",
                        Math.round(rate * 100), alertingProperties.windowMinutes(), success, total);
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
        if (!alertingProperties.enabled()) {
            return;
        }
        try {
            long count = voiceMetrics.turnaroundCount();
            long turns = count - lastTurnaroundCount;
            lastTurnaroundCount = count;
            if (turns < alertingProperties.turnaroundMinTurns()) {
                return;
            }
            long p95 = Math.round(voiceMetrics.turnaroundP95Millis());
            if (p95 > alertingProperties.turnaroundBudgetMs()) {
                log.error("ALERT: turnaround p95 {}ms over the last {} turns exceeds the {}ms budget (§1.3)",
                        p95, turns, alertingProperties.turnaroundBudgetMs());
                notificationService.notify(companyProperties.defaultId(), NotificationType.ERROR_OCCURRED,
                        "Javob kechikmoqda", "Bot javobining kechikishi (p95) " + p95
                                + " ms — belgilangan " + alertingProperties.turnaroundBudgetMs() + " ms dan yuqori", null);
            } else {
                log.info("Turnaround p95 {}ms over the last {} turns", p95, turns);
            }
        } catch (Exception e) {
            log.warn("Turnaround check failed: {}", e.getMessage());
        }
    }

    /**
     * The three ratios that say whether the latency work is paying for itself: how often a
     * reply started before the caller finished turned out to be the right one, how much
     * synthesis the cache spared, and what share of each prompt the provider served from
     * its own cache.
     *
     * <p>Only the first can lose money. A speculative reply the caller invalidates is
     * billed and thrown away, so a hit rate under the threshold means preemptive
     * generation is buying latency with tokens at a bad exchange rate — either
     * {@code preemptive-min-chars} is too low or the recognizer's interims are unstable.
     * The other two are free either way and are logged for the same window.
     */
    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkEfficiency() {
        if (!alertingProperties.enabled()) {
            return;
        }
        try {
            long started = voiceMetrics.speculationStartedCount();
            long hit = voiceMetrics.speculationHitCount();
            long cacheHits = voiceMetrics.ttsCacheHitCount();
            long cacheMisses = voiceMetrics.ttsCacheMissCount();
            long promptTokens = voiceMetrics.llmPromptTokenCount();
            long cachedTokens = voiceMetrics.llmCachedTokenCount();

            long speculations = started - lastSpeculationsStarted;
            long hits = hit - lastSpeculationsHit;
            long ttsHits = cacheHits - lastTtsCacheHits;
            long ttsLookups = ttsHits + (cacheMisses - lastTtsCacheMisses);
            long prompt = promptTokens - lastPromptTokens;
            long cached = cachedTokens - lastCachedTokens;

            lastSpeculationsStarted = started;
            lastSpeculationsHit = hit;
            lastTtsCacheHits = cacheHits;
            lastTtsCacheMisses = cacheMisses;
            lastPromptTokens = promptTokens;
            lastCachedTokens = cachedTokens;

            if (speculations < alertingProperties.minSample()) {
                return; // a handful of turns says nothing about a rate
            }
            long hitRate = hits * 100 / speculations;
            log.info("Pipeline efficiency over the last {} speculations: hit {}% ({}/{}), "
                            + "TTS cache {}%, prompt served from cache {}%",
                    speculations, hitRate, hits, speculations,
                    percent(ttsHits, ttsLookups), percent(cached, prompt));
            if (hitRate < Math.round(alertingProperties.speculationMinHitRate() * 100)) {
                log.warn("ALERT: speculative replies confirmed only {}% of the time ({}/{}) — "
                                + "the misses are billed and thrown away",
                        hitRate, hits, speculations);
            }
        } catch (Exception e) {
            log.warn("Efficiency check failed: {}", e.getMessage());
        }
    }

    private static long percent(long part, long whole) {
        return whole > 0 ? part * 100 / whole : 0;
    }
}
