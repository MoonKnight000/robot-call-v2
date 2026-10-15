package uz.murodjon.robotcallv2.partner.application.port.input;

import uz.murodjon.robotcallv2.partner.application.dto.BrandingResponse;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientRequest;
import uz.murodjon.robotcallv2.partner.application.dto.CreatePartnerClientResponse;
import uz.murodjon.robotcallv2.partner.application.dto.PartnerClientRow;
import uz.murodjon.robotcallv2.partner.application.dto.UpdatePartnerBrandingRequest;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClientFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;

/** The partner (reseller / white-label) program. */
public interface PartnerUseCase {

    /** Platform-only: makes {@code companyId} a partner, or changes its brand. */
    PartnerBranding upsertBrandingByPlatform(long companyId, UpdatePartnerBrandingRequest request);

    /** A partner's own brand; a company that is not a partner is refused. */
    PartnerBranding findOwnBranding(long companyId);

    /** A partner changing its own brand; a company that is not a partner is refused. */
    PartnerBranding updateOwnBranding(long companyId, UpdatePartnerBrandingRequest request);

    /** The brand the company's panel shows: its own as a partner, its partner's as a client, else null. */
    BrandingResponse findBrandingByCompanyId(long companyId);

    /** The brand for the login page on {@code domain}, or null for our own. */
    BrandingResponse findBrandingByDomain(String domain);

    CreatePartnerClientResponse createClient(long partnerCompanyId, CreatePartnerClientRequest request);

    PageableData<PartnerClientRow> findClients(long partnerCompanyId, PartnerClientFilter filter);
}
