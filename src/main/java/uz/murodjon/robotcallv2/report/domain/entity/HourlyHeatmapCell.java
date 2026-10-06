package uz.murodjon.robotcallv2.report.domain.entity;

/**
 * One day×hour cell of the "Kun × soat javob foizi" heatmap (§10.10 Grafik 3).
 *
 * <p>Both the day and the hour are on the company's clock ({@code company_config.timezone}),
 * like the day buckets elsewhere in this package.
 *
 * @param dayOfWeek Postgres {@code extract(dow)}: {@code 0} = Sunday .. {@code 6} = Saturday
 * @param hour      0..23
 */
public record HourlyHeatmapCell(int dayOfWeek, int hour, long total, long answered, double answerRate) {
}

