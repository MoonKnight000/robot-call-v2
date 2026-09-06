package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignVariant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Which A/B variant a target is called with.
 *
 * <p>The assignment is a hash of the number, not a coin toss. A campaign calls the same
 * person again — the first attempt went unanswered, or they asked to be called back
 * tomorrow — and a random draw would put them on the friendly script on Monday and the
 * firm one on Tuesday. Then the conversion is credited to whichever variant happened to
 * be dialled last, and the test measures nothing. Hashing the number instead means the
 * assignment survives every retry without storing it anywhere.
 *
 * <p>The campaign id is mixed in so a number does not land on "the first variant" in every
 * campaign it appears in, which would make two campaigns' results correlated rather than
 * independent.
 *
 * <p>SHA-256 rather than {@link String#hashCode()}: consecutive phone numbers produce
 * consecutive {@code hashCode}s, and consecutive integers modulo a small weight total fall
 * into the buckets in a repeating pattern — a CSV sorted by number would hand one variant
 * whole blocks of the list. SHA-256 has no such structure.
 */
public final class CampaignVariantSelector {

    private CampaignVariantSelector() {
    }

    /**
     * Selects an active variant weighted by {@link CampaignVariant#trafficWeight()}.
     *
     * @param assignmentKey what the assignment is stable for — the target's phone number.
     *                      Null falls back to a random draw, which is right for the calls
     *                      that have no number to be stable for (manual and browser tests)
     *                      and wrong for everything else
     * @return the chosen variant, or null when the campaign has no active variant and the
     *         call should simply run the campaign's own scenario and voice
     */
    public static CampaignVariant selectVariant(List<CampaignVariant> variants, String assignmentKey) {
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        List<CampaignVariant> activeVariants = variants.stream()
                .filter(CampaignVariant::active)
                .toList();
        if (activeVariants.isEmpty()) {
            return null;
        }
        if (activeVariants.size() == 1) {
            return activeVariants.get(0);
        }

        int totalWeight = activeVariants.stream()
                .mapToInt(v -> Math.max(1, v.trafficWeight()))
                .sum();

        int point = assignmentKey == null || assignmentKey.isBlank()
                ? ThreadLocalRandom.current().nextInt(totalWeight)
                : (int) Math.floorMod(hash(activeVariants.get(0).campaignId(), assignmentKey), totalWeight);

        int currentWeightSum = 0;
        for (CampaignVariant variant : activeVariants) {
            currentWeightSum += Math.max(1, variant.trafficWeight());
            if (point < currentWeightSum) {
                return variant;
            }
        }
        return activeVariants.get(activeVariants.size() - 1);
    }

    /** First eight bytes of {@code sha256(campaignId:key)} as a long. */
    private static long hash(long campaignId, String assignmentKey) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest((campaignId + ":" + assignmentKey).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM; if it is missing the platform is broken in
            // ways an A/B test is not going to survive anyway.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[i] & 0xffL);
        }
        return value;
    }
}
