package uz.murodjon.uysotvoice.profile.dto;

/**
 * {@code GET /api/profile/today-stats} — the hover-popover mini-statistics
 * (UI-DESIGN §8.2 "Bugun: 24 qo'ng'iroq / 18m efirda / 92% sifat").
 *
 * <p>Operator-scoped (backend-uchun-talablar.md §6, fixed): only calls transferred to
 * and answered by the caller's own SIP extension count, via {@code
 * call_attempt.operator_user_id} and {@code ReportRepository#operatorTotals}. A
 * bot-only call (nobody's extension answered it) belongs to nobody's personal tally,
 * so an operator who never takes transfers sees all-zero stats here — that is
 * correct, not a bug.
 */
public record TodayStats(long totalCalls, long answeredCalls, double onAirMinutes, double qualityPct) {
}
