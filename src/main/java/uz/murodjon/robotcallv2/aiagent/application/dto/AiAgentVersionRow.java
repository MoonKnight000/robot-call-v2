package uz.murodjon.robotcallv2.aiagent.application.dto;

import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentVersionStats;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentVersionStatus;

import java.time.Instant;

/**
 * One line of an agent's version history — the snapshot itself is fetched per version.
 *
 * @param calls       calls that ran on this version
 * @param conversions of those, the ones that ended in PROMISE_TO_PAY, GOAL_REACHED or COMPLETED
 */
public record AiAgentVersionRow(
        int version,
        AgentVersionStatus status,
        String note,
        Instant publishedAt,
        Long publishedBy,
        String publishedByName,
        long calls,
        long conversions
) {

    /** A version with no calls yet — one just published. */
    public static AiAgentVersionRow of(AiAgentVersion version, String publishedByName) {
        return of(version, publishedByName, AgentVersionStats.none(version.version()));
    }

    public static AiAgentVersionRow of(AiAgentVersion version, String publishedByName, AgentVersionStats stats) {
        return new AiAgentVersionRow(version.version(), version.status(), version.note(),
                version.publishedAt(), version.publishedBy(), publishedByName, stats.calls(), stats.conversions());
    }
}
