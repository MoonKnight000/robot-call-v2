package uz.murodjon.robotcallv2.callrecord.domain.entity;

import java.time.Instant;

/**
 * An answered call, reduced to what deciding "did this call cause that" needs.
 *
 * <p>Deliberately not {@link CallAttempt}: the question is asked once per conversion event
 * a customer's system posts, over every call to one number, and loading a whole
 * conversation to compare two timestamps would be a page of joins for nothing.
 *
 * @param campaignId  the campaign this call belonged to, or null for an inbound or manual call
 * @param variantId   the A/B variant it ran, or null when the campaign is not testing
 * @param answeredAt  when the caller picked up — never null, an unanswered call is not one of these
 */
public record AnsweredCall(
        long callAttemptId,
        Long campaignId,
        Long variantId,
        Instant answeredAt
) {
}
