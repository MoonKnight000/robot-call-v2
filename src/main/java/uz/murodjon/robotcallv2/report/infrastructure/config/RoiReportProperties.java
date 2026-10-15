package uz.murodjon.robotcallv2.report.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The default operator yardstick of the ROI report ({@code voice-agent.report.roi.*});
 * a request may name its own hourly cost.
 *
 * @param operatorHourlyCostUzs     an operator's hour, salary and taxes included
 * @param operatorSecondsPerAttempt what each call costs an operator besides the talking
 * @param operatorHoursPerMonth     one full-time operator's working hours in a month
 */
@ConfigurationProperties(prefix = "voice-agent.report.roi")
public record RoiReportProperties(
        long operatorHourlyCostUzs,
        int operatorSecondsPerAttempt,
        int operatorHoursPerMonth
) {
}
