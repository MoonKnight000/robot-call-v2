package uz.murodjon.robotcallv2.partner.application.port.output;

import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClient;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClientFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

public interface PartnerRepository {

    /** The company's brand, or null when it is not a partner. */
    PartnerBranding findBrandingByCompanyId(long companyId);

    /** The brand of the partner whose console runs on {@code domain}, or null. */
    PartnerBranding findBrandingByDomain(String domain);

    boolean existsBrandingByDomainAndCompanyIdNot(String domain, long companyId);

    /** Creates or replaces the company's brand — creating it makes the company a partner. */
    PartnerBranding upsertBranding(long companyId, PartnerBranding branding);

    void createClient(long partnerCompanyId, long clientCompanyId);

    /** The partner that brought {@code clientCompanyId} in, or null for a direct customer. */
    PartnerClient findClientByClientCompanyId(long clientCompanyId);

    PageableData<PartnerClient> findClientPage(long partnerCompanyId, PartnerClientFilter filter);
}
