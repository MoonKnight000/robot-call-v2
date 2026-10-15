package uz.murodjon.robotcallv2.campaign.domain.entity;

/**
 * How a campaign follows up after one particular disposition: when it calls again, and
 * whether it texts the person in between.
 *
 * @param maxAttempts  give up on the target once this many attempts have been spent and
 *                     the latest one ended this way — {@code null} or 0 keeps the campaign's
 *                     overall {@code maxAttempts}. "One voicemail is enough" is
 *                     {@code VOICEMAIL: {maxAttempts: 1}}
 * @param delayMinutes minutes before the next attempt — {@code null} or 0 keeps the
 *                     deployment default for that disposition
 * @param smsText      an SMS sent to the number when a call ends this way, with the target's
 *                     facts filled in ({@code {{clientName | Hurmatli mijoz}}}); null sends none.
 *                     "No answer → text them, call again in three hours" is
 *                     {@code NO_ANSWER: {delayMinutes: 180, smsText: "...", smsOnAttempt: 1}}
 * @param smsOnAttempt send the SMS only when the call that ended this way was this attempt of
 *                     the target, so a retried number is texted once rather than after every
 *                     ring-out; null sends it every time
 */
public record RetryRule(Integer maxAttempts, Integer delayMinutes, String smsText, Integer smsOnAttempt) {
}
