package uz.murodjon.robotcallv2.live.domain.entity;

/**
 * The one number a periodic {@code GET /api/reports/dashboard/kpi} cannot give a
 * dashboard between polls: how many calls are on the line right now
 * ({@link uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics#activeCalls()}). Everything
 * else on that dashboard (totals, change%, sparkline) is a historical aggregate the
 * existing endpoint already computes well — republishing it every couple of seconds
 * would just re-run the same DB query for no new information.
 *
 * @param activeCalls calls currently in progress across the whole instance
 */
public record LiveKpiSnapshot(int activeCalls) {
}

