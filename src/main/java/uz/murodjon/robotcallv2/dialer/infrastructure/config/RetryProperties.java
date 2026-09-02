package uz.murodjon.robotcallv2.dialer.infrastructure.config;

/**
 * How long to wait before retrying, by why the last attempt failed. These apply only to
 * campaigns that set no {@code retry_interval_minutes} of their own — a campaign that
 * names an interval means it for every outcome (see {@code RetrySchedule.delayFor}).
 */
public record RetryProperties(
        int noAnswerMinutes,
        int failedMinutes,
        int voicemailMinutes,
        boolean respectDialWindow
) {
}
