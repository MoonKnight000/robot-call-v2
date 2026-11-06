package uz.murodjon.robotcallv2.knowledgebase.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.knowledgebase.application.port.output.KnowledgeSourceRepository;
import uz.murodjon.robotcallv2.knowledgebase.domain.entity.KnowledgeSource;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceStatus;
import uz.murodjon.robotcallv2.knowledgebase.domain.enums.KnowledgeSourceType;
import uz.murodjon.robotcallv2.knowledgebase.infrastructure.config.KnowledgeBaseProperties;
import uz.murodjon.robotcallv2.shared.lock.ClusterJobLock;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Fetches a URL catalog feed again once its {@code refreshMinutes} have passed
 * (UNIVERSAL-OPERATOR-PLAN B.4): prices and stock change during the day, and a caller told
 * yesterday's price is told a figure the shop will not honour.
 *
 * <p>Only an indexed feed is refreshed. A failed one is not fetched every minute — hammering a
 * feed that answers 500 will not change it — but a transient failure is retried a few times
 * with a doubling wait ({@link #enqueueDueRetries}). Moving the source back to {@code PENDING}
 * is what keeps the next tick from queueing it twice.
 */
@Service
public class CatalogFeedRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(CatalogFeedRefreshScheduler.class);

    private final KnowledgeSourceRepository sourceRepository;
    private final KnowledgeIndexingService indexingService;
    /** One sweep at a time across the cluster — two at once would queue the same feed twice. */
    private final ClusterJobLock clusterJobLock;
    private final Clock clock;
    private final KnowledgeBaseProperties knowledgeBaseProperties;

    public CatalogFeedRefreshScheduler(KnowledgeSourceRepository sourceRepository,
                                       KnowledgeIndexingService indexingService,
                                       ClusterJobLock clusterJobLock,
                                       Clock clock,
                                       KnowledgeBaseProperties knowledgeBaseProperties) {
        this.sourceRepository = sourceRepository;
        this.indexingService = indexingService;
        this.clusterJobLock = clusterJobLock;
        this.clock = clock;
        this.knowledgeBaseProperties = knowledgeBaseProperties;
    }

    @Scheduled(fixedDelay = 60_000)
    public void refreshDueFeeds() {
        clusterJobLock.runExclusively("knowledge.catalog-feed-refresh", this::enqueueDueFeeds);
    }

    private void enqueueDueFeeds() {
        Instant now = clock.instant();
        enqueueDueRetries(now);
        for (KnowledgeSourceType type : List.of(KnowledgeSourceType.STRUCTURED_FEED, KnowledgeSourceType.SITEMAP, KnowledgeSourceType.URL)) {
            for (KnowledgeSource source : sourceRepository.findRefreshable(type, KnowledgeSourceStatus.INDEXED)) {
                if (source.isDueForRefresh(now)) {
                    indexingService.enqueue(sourceRepository.save(source.queuedForIndexing()));
                    log.info("[company {}] {} source {} ('{}') is due, queued for refresh",
                            source.companyId(), type, source.id(), source.name());
                }
            }
        }
    }

    /**
     * A source that failed for a transient reason (site down, fetch cut, unexpected error) is queued
     * again with a doubling wait, up to {@code autoRetryMaxAttempts} times; a permanent failure (no text,
     * bad address) is left for a person, as before.
     */
    private void enqueueDueRetries(Instant now) {
        int maxAttempts = knowledgeBaseProperties.autoRetryMaxAttempts();
        if (maxAttempts <= 0) {
            return;
        }
        Duration baseWait = Duration.ofMinutes(knowledgeBaseProperties.autoRetryBaseMinutes());
        for (KnowledgeSource source : sourceRepository.findByStatus(KnowledgeSourceStatus.FAILED)) {
            if (source.isDueForAutoRetry(now, maxAttempts, baseWait)) {
                indexingService.enqueue(sourceRepository.save(source.queuedForIndexing()));
                log.info("[company {}] Knowledge source {} ('{}') failed with {} — auto-retry {} of {}",
                        source.companyId(), source.id(), source.name(), source.errorCode(),
                        source.failedAttempts(), maxAttempts);
            }
        }
    }
}
