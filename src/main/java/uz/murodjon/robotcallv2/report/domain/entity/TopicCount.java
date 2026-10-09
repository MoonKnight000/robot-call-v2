package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * What scored operator calls were about, as the judge tagged them (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.4).
 *
 * @param calls            scored calls carrying the tag
 * @param averageScore     their score as it stands; null when none had one
 * @param upsetClientCalls of those, the ones whose client the judge read as NEGATIVE or HOSTILE
 */
public record TopicCount(
        String topic,
        long calls,
        Double averageScore,
        long upsetClientCalls
) {
}
