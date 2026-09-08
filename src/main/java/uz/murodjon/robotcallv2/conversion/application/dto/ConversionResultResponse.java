package uz.murodjon.robotcallv2.conversion.application.dto;

/**
 * What ingest answers: whether the event was credited to a call, and to which.
 *
 * <p>Answered synchronously so the poster learns straight away that its number matched
 * nothing — a webhook that always returns 200 and quietly drops half the events is how a
 * campaign's numbers end up wrong with nobody noticing.
 *
 * @param attributed      false when nothing in the window qualified
 * @param reason          why not, when {@code attributed} is false; null otherwise
 * @param duplicate       true when this key had already been posted; nothing was changed
 * @param campaignId      the campaign credited, when there is one
 * @param variantId       the A/B variant credited, when the campaign was testing
 * @param callAttemptId   the call credited
 */
public record ConversionResultResponse(
        long conversionEventId,
        boolean attributed,
        String reason,
        boolean duplicate,
        Long campaignId,
        Long variantId,
        Long callAttemptId
) {
}
