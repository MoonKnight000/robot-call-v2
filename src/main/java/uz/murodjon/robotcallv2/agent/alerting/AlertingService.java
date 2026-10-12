package uz.murodjon.robotcallv2.agent.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.metrics.*;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.notification.application.port.input.NotificationUseCase;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Periodically checks what a degrading deployment shows up in first: the recent
 * call success rate ("success" = a conversion, {@link Disposition#isConversion}: a promise
 * to pay or a completed scenario — the definition the reports use), the §1.3 turnaround budget, and
 * whether the pipeline's own optimizations are still paying for themselves.
 * Each is logged as an alert when it crosses its threshold (PROJECT.md §10 Bosqich 12).
 * Log-based by design — wire the WARN log to your alerting sink (e.g. Loki/Alertmanager).
 * Best-effort: a DB or metric error is logged and skipped.
 */
@Service
public class AlertingService {

    private static final Logger log = LoggerFactory.getLogger(AlertingService.class);

    private final CallAttemptUseCase callAttemptUseCase;
    private final NotificationUseCase notificationService;
    private final CompanyProperties companyProperties;
    private final DialogMetrics dialogMetrics;
    private final LlmMetrics llmMetrics;
    private final SttMetrics sttMetrics;
    private final TtsMetrics ttsMetrics;
    private final RealtimeMetrics realtimeMetrics;
    private final RtpMetrics rtpMetrics;
    private final AlertingProperties alertingProperties;
    /**
     * Held by {@link #checkSuccessRate} only: it is the one check that reads shared data. The
     * other three read this JVM's own metrics, which every instance has to watch for itself.
     */
    private final ClusterJobLock clusterJobLock;

    /** Turns already counted when {@link #checkTurnaround} last ran — the window is the delta. */
    private long lastTurnaroundCount;

    /** Counters as {@link #checkEfficiency} last saw them; every ratio below is a delta. */
    private long lastSpeculationsStarted;
    private long lastSpeculationsHit;
    private long lastTtsCacheHits;
    private long lastTtsCacheMisses;
    private long lastPromptTokens;
    private long lastCachedTokens;

    /** Failover counters and MOS totals as {@link #checkProviderHealth} last saw them. */
    private double lastFailovers;
    private double lastMosCount;
    private double lastMosTotal;

    public AlertingService(CallAttemptUseCase callAttemptUseCase,
                           NotificationUseCase notificationService,
                           CompanyProperties companyProperties,
                           DialogMetrics dialogMetrics,
                           LlmMetrics llmMetrics,
                           SttMetrics sttMetrics,
                           TtsMetrics ttsMetrics,
                           RealtimeMetrics realtimeMetrics,
                           RtpMetrics rtpMetrics,
                           AlertingProperties alertingProperties,
                           ClusterJobLock clusterJobLock) {
        this.clusterJobLock = clusterJobLock;
        this.callAttemptUseCase = callAttemptUseCase;
        this.notificationService = notificationService;
        this.companyProperties = companyProperties;
        this.dialogMetrics = dialogMetrics;
        this.llmMetrics = llmMetrics;
        this.sttMetrics = sttMetrics;
        this.ttsMetrics = ttsMetrics;
        this.realtimeMetrics = realtimeMetrics;
        this.rtpMetrics = rtpMetrics;
        this.alertingProperties = alertingProperties;
    }

    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkSuccessRate() {
        if (!alertingProperties.enabled()) {
            return;
        }
        clusterJobLock.runExclusively("alerting.success-rate", this::evaluateSuccessRate);
    }

    private void evaluateSuccessRate() {
        try {
            Instant since = Instant.now().minus(alertingProperties.windowMinutes(), ChronoUnit.MINUTES);
            long total = callAttemptUseCase.countEndedSince(since);
            if (total < alertingProperties.minSample()) {
                return; // not enough data to judge
            }
            long success = 0;
            for (Disposition disposition : Disposition.values()) {
                if (disposition.isConversion()) {
                    success += callAttemptUseCase.countEndedSinceWithDisposition(since, disposition);
                }
            }
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
            long count = dialogMetrics.turnaroundCount();
            long turns = count - lastTurnaroundCount;
            lastTurnaroundCount = count;
            if (turns < alertingProperties.turnaroundMinTurns()) {
                return;
            }
            long p95 = Math.round(dialogMetrics.turnaroundP95Millis());
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
            long started = llmMetrics.speculationStartedCount();
            long hit = llmMetrics.speculationHitCount();
            long cacheHits = ttsMetrics.ttsCacheHitCount();
            long cacheMisses = ttsMetrics.ttsCacheMissCount();
            long promptTokens = llmMetrics.llmPromptTokenCount();
            long cachedTokens = llmMetrics.llmCachedTokenCount();

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

    /**
     * The two things that go wrong underneath a conversation and never show up in the
     * success rate until much later: a vendor that keeps failing (and is being covered by
     * failover, so every call still "works"), and a network that is chewing the audio.
     *
     * <p>Failovers are summed across STT, TTS and realtime engines: which vendor it is,
     * the tags on the counters say ({@code voice.stt.failover{from,to}},
     * {@code voice.realtime.fallbacks{engine}}); that one is, is what needs a human now.
     */
    @Scheduled(fixedDelayString = "#{${voice-agent.alerting.check-minutes:5} * 60 * 1000}")
    public void checkProviderHealth() {
        if (!alertingProperties.enabled()) {
            return;
        }
        try {
            double failovers = sttMetrics.countFailovers()
                    + ttsMetrics.countFailovers()
                    + realtimeMetrics.countFallbacks();
            double window = failovers - lastFailovers;
            lastFailovers = failovers;
            if (window > alertingProperties.providerFailoverThreshold()) {
                log.error("ALERT: {} provider failovers since the last check (threshold {}) — "
                                + "a speech vendor is failing and failover is covering it",
                        Math.round(window), alertingProperties.providerFailoverThreshold());
                notificationService.notify(companyProperties.defaultId(), NotificationType.ERROR_OCCURRED,
                        "Provayder nosoz", "Oxirgi tekshiruvdan beri " + Math.round(window)
                                + " marta STT/TTS/realtime provayder almashtirildi — provayderlardan biri ishlamayapti",
                        null);
            }

            double[] mos = rtpMetrics.mosTotals();
            double calls = mos[0] - lastMosCount;
            double total = mos[1] - lastMosTotal;
            lastMosCount = mos[0];
            lastMosTotal = mos[1];
            if (calls >= alertingProperties.minSample()) {
                double mean = total / calls;
                if (mean < alertingProperties.minMeanMos()) {
                    log.error("ALERT: mean inbound MOS {} over the last {} calls is below {} — "
                                    + "the trunk or the network is degrading the audio",
                            Math.round(mean * 100) / 100.0, Math.round(calls), alertingProperties.minMeanMos());
                    notificationService.notify(companyProperties.defaultId(), NotificationType.ERROR_OCCURRED,
                            "Ovoz sifati past", "Oxirgi " + Math.round(calls) + " ta qo'ng'iroqda o'rtacha MOS "
                                    + Math.round(mean * 100) / 100.0 + " — trunk yoki tarmoq ovozni buzmoqda", null);
                }
            }
        } catch (Exception e) {
            log.warn("Provider health check failed: {}", e.getMessage());
        }
    }

    private static long percent(long part, long whole) {
        return whole > 0 ? part * 100 / whole : 0;
    }
}
