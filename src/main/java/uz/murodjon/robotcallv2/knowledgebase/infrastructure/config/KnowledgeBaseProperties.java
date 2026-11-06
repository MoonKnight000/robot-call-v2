package uz.murodjon.robotcallv2.knowledgebase.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Knowledge base runtime limits ({@code config/misc.yml}, {@code voice-agent.knowledge-base}).
 *
 * @param queryEmbedTimeoutMs   how long a caller's question may wait on the embedding model before the
 *                              search goes on with keywords alone; the call is waiting on this
 * @param autoRetryMaxAttempts  how many times a source that failed for a transient reason (fetch error,
 *                              unexpected failure) is queued again on its own; 0 turns auto-retry off
 * @param autoRetryBaseMinutes  wait before the first auto-retry; each next one waits twice as long
 * @param itemMinScore          how close in meaning a curated answer must be to be spoken without one of its
 *                              keywords — higher than the passage floor, because it is read out word for word
 * @param sitemapMaxPages       how many pages one sitemap source reads
 */
@ConfigurationProperties(prefix = "voice-agent.knowledge-base")
public record KnowledgeBaseProperties(
        long queryEmbedTimeoutMs,
        int autoRetryMaxAttempts,
        int autoRetryBaseMinutes,
        double itemMinScore,
        int sitemapMaxPages
) {
    public KnowledgeBaseProperties {
        if (queryEmbedTimeoutMs <= 0) {
            queryEmbedTimeoutMs = 800;
        }
        if (autoRetryMaxAttempts < 0) {
            autoRetryMaxAttempts = 0;
        }
        if (autoRetryBaseMinutes <= 0) {
            autoRetryBaseMinutes = 5;
        }
        if (itemMinScore <= 0 || itemMinScore > 1) {
            itemMinScore = 0.80;
        }
        if (sitemapMaxPages <= 0) {
            sitemapMaxPages = 100;
        }
    }
}
