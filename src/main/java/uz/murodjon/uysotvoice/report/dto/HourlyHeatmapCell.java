package uz.murodjon.uysotvoice.report.dto;

/**
 * One day×hour cell of the "Kun × soat javob foizi" heatmap (§10.10 Grafik 3).
 *
 * @param dayOfWeek Postgres {@code extract(dow)}: {@code 0} = Sunday .. {@code 6} = Saturday
 * @param hour      0..23, in the database's stored (UTC) time, same convention as {@code
 *                  date_trunc} elsewhere in this package
 */
public record HourlyHeatmapCell(int dayOfWeek, int hour, long total, long answered, double answerRate) {
}
