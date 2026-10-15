package uz.murodjon.robotcallv2.partner.application.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.domain.entity.Company;
import uz.murodjon.robotcallv2.partner.application.dto.BrandingResponse;
import uz.murodjon.robotcallv2.partner.application.dto.PartnerClientRow;
import uz.murodjon.robotcallv2.partner.application.dto.UpdatePartnerBrandingRequest;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerBranding;
import uz.murodjon.robotcallv2.partner.domain.entity.PartnerClient;

import java.util.Locale;

@Component
public class PartnerMapper {

    /** The request as a brand to write; the domain lower-cased, since hosts are compared that way. */
    public PartnerBranding toPartnerBranding(UpdatePartnerBrandingRequest request) {
        String domain = request.domain() == null || request.domain().isBlank()
                ? null : request.domain().trim().toLowerCase(Locale.ROOT);
        return new PartnerBranding(0, request.productName().trim(), domain, blankToNull(request.logoUrl()),
                blankToNull(request.primaryColor()), blankToNull(request.supportEmail()),
                blankToNull(request.supportPhone()), null);
    }

    public BrandingResponse toBrandingResponse(PartnerBranding branding) {
        return branding == null ? null : new BrandingResponse(branding.productName(), branding.logoUrl(),
                branding.primaryColor(), branding.supportEmail(), branding.supportPhone());
    }

    /** {@code company} is null for a client the company feature no longer finds — its id still shows. */
    public PartnerClientRow toPartnerClientRow(PartnerClient client, Company company) {
        return new PartnerClientRow(client.clientCompanyId(), company != null ? company.name() : null,
                company != null ? company.status() : null, client.createdAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
