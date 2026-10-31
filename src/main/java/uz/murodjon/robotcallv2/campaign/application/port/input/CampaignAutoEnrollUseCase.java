package uz.murodjon.robotcallv2.campaign.application.port.input;

import uz.murodjon.robotcallv2.campaign.application.dto.AutoEnrollPreviewResponse;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;

public interface CampaignAutoEnrollUseCase {

    /**
     * Who the rules match right now — read live, nothing is enrolled; one page of them, oldest first, with the count
     * of all. Both kinds: an AUTO_ENROLL campaign's trigger, a STANDARD campaign's "Filter contacts" list (its
     * unticked contacts are listed too). {@code draft} is the rules being edited and not saved yet; null reads the
     * saved ones. 404 when the company has no such campaign.
     */
    AutoEnrollPreviewResponse previewAutoEnroll(long companyId, long campaignId, AutoEnrollment draft, int page, int size);
}
