package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * The deployment's per-disposition retry waits, for campaigns that set no interval of
 * their own ({@code RetrySchedule.delayFor}). A value of 0 or less falls back to the
 * schedule's built-in default for that disposition.
 */
public record RetryDelays(int noAnswerMinutes, int failedMinutes, int voicemailMinutes) {
}
