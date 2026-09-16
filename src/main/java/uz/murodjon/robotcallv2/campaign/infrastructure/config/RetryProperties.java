package uz.murodjon.robotcallv2.campaign.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import uz.murodjon.robotcallv2.campaign.domain.entity.RetryDelays;

/**
 * How long to wait before retrying, by why the last attempt failed. Bound from
 * {@code voice-agent.dialer.retry.*}; read by CampaignService, which owns the retry schedule. These apply only to
 * campaigns that set no {@code retry_interval_minutes} of their own — a campaign that
 * names an interval means it for every outcome (see {@code RetrySchedule.delayFor}).
 *
 * <p>Split by reason because one 24-hour interval for everything spent most of a
 * campaign's three retries on the outcome least likely to have changed.
 *
 * @param noAnswerMinutes   rang out or busy: the subscriber exists and may well be free
 *                          this afternoon, so this is the short one
 * @param failedMinutes     our fault or the carrier's. Nothing about the subscriber
 *                          changed, so retry soon
 * @param voicemailMinutes  a machine answers at every hour equally, so the point is to
 *                          land on a different hour — deliberately NOT a multiple of 24
 * @param respectDialWindow pull the computed time into the campaign's dial window and
 *                          allowed weekdays (§11.2), so {@code next_attempt_at} never
 *                          reads 03:00 on a Sunday
 */
@ConfigurationProperties(prefix = "voice-agent.dialer.retry")
public record RetryProperties(
        int noAnswerMinutes,
        int failedMinutes,
        int voicemailMinutes,
        boolean respectDialWindow
) {

    /** The per-disposition waits, as the retry schedule takes them. */
    public RetryDelays toRetryDelays() {
        return new RetryDelays(noAnswerMinutes, failedMinutes, voicemailMinutes);
    }
}
