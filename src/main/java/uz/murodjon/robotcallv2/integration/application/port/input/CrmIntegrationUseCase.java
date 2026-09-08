package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.integration.application.dto.AuthorizeUrlResponse;
import uz.murodjon.robotcallv2.integration.application.dto.ConnectIntegrationRequest;
import uz.murodjon.robotcallv2.integration.application.dto.CrmCatalogEntry;
import uz.murodjon.robotcallv2.integration.application.dto.CrmIntegrationRow;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface CrmIntegrationUseCase {

    List<CrmCatalogEntry> catalog();

    CrmIntegrationRow findByCompanyId(long companyId);

    CrmIntegrationRow connect(long companyId, ConnectIntegrationRequest request);

    AuthorizeUrlResponse buildAuthorizeUrl(long companyId);

    /**
     * Turns the authorization code Uysot redirected back with into stored tokens.
     *
     * @return where to send the browser next — a page of this platform's own UI carrying
     *         {@code ?crm=connected}, or {@code ?crm=error&reason=<ErrorCode>} when the
     *         exchange failed. A failure is reported this way rather than thrown because
     *         the caller here is a person's browser arriving from another site: a JSON
     *         error body would strand them on a blank page with no way back
     */
    URI handleCallback(String code, String state);

    Optional<String> currentAccessToken(long companyId);

    void disconnect(long companyId);
}
