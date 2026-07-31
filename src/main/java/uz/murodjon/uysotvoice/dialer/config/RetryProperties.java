package uz.murodjon.uysotvoice.dialer.config;

/**
 * How long to wait before retrying, by why the last attempt failed. The campaign's
 * own {@code retry_interval_hours} still covers everything not listed here.
 *
 * @param noAnswerMinutes   subscriber did not pick up — they may be free within hours
 * @param failedMinutes     technical/carrier failure — nothing about the subscriber
 *                          changed, so retry soon
 * @param voicemailMinutes  an answering machine answered. Calling back 24h later
 *                          reaches the same machine, so this should not be a multiple
 *                          of a day
 * @param respectDialWindow move a computed retry time into the campaign's dial window
 *                          and allowed weekdays (§11.2). Off means {@code
 *                          next_attempt_at} can read 03:00 Sunday, and the dialer
 *                          holds the target until the window reopens
 */
public record RetryProperties(
        int noAnswerMinutes,
        int failedMinutes,
        int voicemailMinutes,
        boolean respectDialWindow
) {
}
