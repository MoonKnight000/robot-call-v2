package uz.murodjon.robotcallv2.dialer.application.port.input;

import uz.murodjon.robotcallv2.dialer.application.dto.PreviewCardResponse;
import uz.murodjon.robotcallv2.dialer.application.dto.PreviewDialResponse;

/**
 * PREVIEW campaigns, from the current operator's side (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md
 * §1.2): take a card, then dial or skip it; the call's wrap-up settles the card.
 */
public interface PreviewDialUseCase {

    /**
     * The card the operator holds in the campaign, or a newly claimed one; null when no
     * client is due now.
     */
    PreviewCardResponse findNextCard(long companyId, long campaignId);

    /** Rings the operator, then dials the client of the card they hold. */
    PreviewDialResponse callTarget(long companyId, long campaignId, long targetId);

    /** Puts the undialled card back into the campaign, its attempt not counted. */
    void releaseTarget(long companyId, long campaignId, long targetId);
}
