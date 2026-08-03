package uz.murodjon.uysotvoice.report.dto;

/**
 * One stage of the "Qo'ng'iroq → Javob → Shaxs tasdiqlandi → Suhbat → Natija" funnel
 * (§10.10 Grafik 6).
 *
 * @param stage constant id, in funnel order: {@code CALL}, {@code ANSWERED},
 *              {@code PERSON_CONFIRMED}, {@code CONVERSATION}, {@code RESULT}
 * @param count calls that reached this stage
 * @param rate  {@code count} / the funnel's total calls (0..1) — conversion from the
 *              very top, not from the previous stage, so the chart reads as "what
 *              share of everything dialled got this far"
 */
public record FunnelStage(String stage, long count, double rate) {
}
