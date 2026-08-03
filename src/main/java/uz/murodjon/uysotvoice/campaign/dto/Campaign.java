package uz.murodjon.uysotvoice.campaign.dto;

import uz.murodjon.uysotvoice.campaign.enums.CampaignStatus;
import uz.murodjon.uysotvoice.campaign.enums.CampaignType;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * A row of {@code campaign} (PROJECT.md §6).
 *
 * @param dialDays weekdays this campaign may dial on (§11.2); empty means every day
 * @param ttsVoice catalog id of the voice this campaign speaks with (§2.5); null keeps
 *                 the configured provider/voice routing
 * @param dailyCallCap most calls this campaign may dial in one day; 0 = unlimited. A cost
 *                 control, not a rate limit — {@code dispatch_batch} and
 *                 {@code max_concurrent_calls} shape the pace, this bounds the total
 * @param scenarioId the specific scenario row this campaign runs (ROADMAP A.3) —
 *                 fixed at creation, so editing that scenario later never changes what
 *                 this campaign's calls do
 * @param companyId owning company (ROADMAP B.1) — decides which SIP trunk the dialer
 *                 originates this campaign's calls through (ROADMAP B.3)
 * @param disclosureEnabled whether the §11.1 opening disclosure ("Assalomu alaykum!
 *                 Bu Uysot kompaniyasining avtomatik ovozli xizmati...") is spoken on
 *                 this campaign's calls; the global {@code voice-agent.dialog.mandatory-
 *                 disclosure} switch still applies on top as a hard kill-switch
 * @param createdBy {@code app_user} who created this campaign; {@code null} for a
 *                 campaign created via the machine-to-machine {@code X-Api-Key} (no
 *                 associated person) or one created before this column was wired up
 */
public record Campaign(
        long id,
        String name,
        CampaignType type,
        CampaignStatus status,
        String goalPrompt,
        String defaultLanguage,
        LocalTime dialWindowStart,
        LocalTime dialWindowEnd,
        Set<DayOfWeek> dialDays,
        int maxAttempts,
        int retryIntervalHours,
        int maxConcurrentCalls,
        String ttsVoice,
        int dailyCallCap,
        long scenarioId,
        long companyId,
        boolean disclosureEnabled,
        Long createdBy
) {

    /**
     * Weekdays this campaign may dial on. An empty set allows every day — a campaign
     * created without one must not silently stop dialing, and the dial window still
     * applies.
     */
    public Set<DayOfWeek> allowedDays() {
        return dialDays == null || dialDays.isEmpty() ? EnumSet.allOf(DayOfWeek.class) : dialDays;
    }
}
