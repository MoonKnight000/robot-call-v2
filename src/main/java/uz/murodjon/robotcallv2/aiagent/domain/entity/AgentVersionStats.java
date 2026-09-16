package uz.murodjon.robotcallv2.aiagent.domain.entity;

/**
 * What one published version of an agent achieved on calls (NEXT-PLAN A.1).
 *
 * @param calls       calls that ran on this version
 * @param conversions of those, the ones that ended in PROMISE_TO_PAY, GOAL_REACHED or COMPLETED
 */
public record AgentVersionStats(
        int version,
        long calls,
        long conversions
) {

    public static AgentVersionStats none(int version) {
        return new AgentVersionStats(version, 0, 0);
    }
}
