package uz.murodjon.robotcallv2.campaign.domain.service;

import uz.murodjon.robotcallv2.campaign.domain.enums.CampaignStatus;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Which status a campaign may move to from where. Pure rules, no Spring:
 *
 * <pre>
 * DRAFT ──► SCHEDULED ──► ACTIVE ◄──► PAUSED
 *   │            │           │           │
 *   └────────────┘           ▼           │
 *   (start now)          COMPLETED ──► ACTIVE (a recurrence run, or a manual restart)
 *   └───────► ARCHIVED ◄──────────────────┘   (from every status; nothing leaves ARCHIVED)
 * </pre>
 *
 * A status set to what it already is passes: a second "start" on a running campaign is
 * a no-op, not an error.
 */
public final class CampaignStateMachine {

    private static final Map<CampaignStatus, Set<CampaignStatus>> ALLOWED = Map.of(
            CampaignStatus.DRAFT, EnumSet.of(CampaignStatus.SCHEDULED, CampaignStatus.ACTIVE, CampaignStatus.ARCHIVED),
            CampaignStatus.SCHEDULED, EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.ARCHIVED),
            CampaignStatus.ACTIVE, EnumSet.of(CampaignStatus.PAUSED, CampaignStatus.COMPLETED, CampaignStatus.ARCHIVED),
            CampaignStatus.PAUSED, EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.ARCHIVED),
            CampaignStatus.COMPLETED, EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.ARCHIVED),
            CampaignStatus.ARCHIVED, EnumSet.noneOf(CampaignStatus.class)
    );

    private CampaignStateMachine() {
    }

    public static boolean canMove(CampaignStatus from, CampaignStatus to) {
        return from == to || ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    /** @throws ConflictException when {@code to} is not reachable from {@code from} */
    public static void requireTransition(long campaignId, CampaignStatus from, CampaignStatus to) {
        if (!canMove(from, to)) {
            throw new ConflictException(ErrorCode.CAMPAIGN_INVALID_TRANSITION, campaignId, from, to);
        }
    }
}
