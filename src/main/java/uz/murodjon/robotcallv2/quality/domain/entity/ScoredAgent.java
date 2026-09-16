package uz.murodjon.robotcallv2.quality.domain.entity;

/**
 * One agent that had calls in a window, and how many (UNIVERSAL-OPERATOR-PLAN D.3).
 *
 * <p>Carries the company because the daily job runs under no request and therefore under no
 * company: it walks every tenant's agents, and the company id travels as an argument from
 * here on (CLAUDE.md §4).
 */
public record ScoredAgent(long companyId, long aiAgentId, long calls) {
}
