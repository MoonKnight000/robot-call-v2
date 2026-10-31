package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollCandidate;
import uz.murodjon.robotcallv2.campaign.domain.entity.AutoEnrollment;

import java.util.List;

/**
 * The company's contacts as an auto-enroll campaign's rules see them. Rules that are empty
 * match nobody.
 */
public interface AutoEnrollContactRepository {

    /**
     * The company's contacts meeting the rules, oldest first.
     *
     * @param excludeEnrolledInCampaignId leaves out contacts whose number is already a target of
     *                                    this campaign, in any status — a contact whose call
     *                                    finished is not enrolled a second time; null leaves
     *                                    none out
     */
    List<AutoEnrollCandidate> findMatching(long companyId, AutoEnrollment autoEnroll,
                                           Long excludeEnrolledInCampaignId, int limit);

    /** One page of the company's contacts meeting the rules, oldest first — the list a STANDARD campaign's filter shows. */
    List<AutoEnrollCandidate> findMatchingPage(long companyId, AutoEnrollment autoEnroll, int offset, int limit);

    /** How many of the company's contacts meet the rules, less {@code excludedContactIds}. */
    long countMatching(long companyId, AutoEnrollment autoEnroll);
}
