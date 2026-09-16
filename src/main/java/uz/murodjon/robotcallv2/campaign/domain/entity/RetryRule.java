package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * How a campaign retries after one particular disposition.
 *
 * @param maxAttempts  give up on the target once this many attempts have been spent and
 *                     the latest one ended this way — {@code null} or 0 keeps the campaign's
 *                     overall {@code maxAttempts}. "One voicemail is enough" is
 *                     {@code VOICEMAIL: {maxAttempts: 1}}
 * @param delayMinutes minutes before the next attempt — {@code null} or 0 keeps the
 *                     deployment default for that disposition
 */
public record RetryRule(Integer maxAttempts, Integer delayMinutes) {
}
