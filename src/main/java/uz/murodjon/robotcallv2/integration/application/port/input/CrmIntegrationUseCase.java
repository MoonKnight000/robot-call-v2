package uz.murodjon.robotcallv2.integration.application.port.input;

import uz.murodjon.robotcallv2.integration.application.dto.*;
import uz.murodjon.robotcallv2.integration.domain.entity.CrmConnection;

import java.net.URI;
import java.util.List;
import java.util.Optional;

public interface CrmIntegrationUseCase {

    /** The catalog as the company sees it: what the platform has switched off for it is marked, not hidden. */
    List<IntegrationCatalogEntry> findCatalog(long companyId);

    CrmIntegrationRow findByCompanyId(long companyId);

    CrmIntegrationRow connect(long companyId, ConnectIntegrationRequest request);

    /**
     * Connects amoCRM, Kommo or Bitrix24 by account address and token, replacing whatever
     * the company was connected to. The address must be a public {@code https} one.
     */
    CrmIntegrationRow connectAccount(long companyId, ConnectCrmAccountRequest request);

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

    /** The company's Uysot OAuth token, refreshed when due; never another provider's token. */
    Optional<String> findCurrentAccessToken(long companyId);

    /**
     * How the call pipeline reaches this company's CRM, decrypted: the provider it chose
     * and, once connected, the address and token. For Uysot the address is platform
     * configuration and is left {@code null}. Empty when the company has no integration.
     */
    Optional<CrmConnection> findConnection(long companyId);

    /**
     * Asks the connected CRM account whether the saved address and credential still work, and
     * records the answer: {@code CONNECTED} when it does, {@code ERROR} when it does not.
     * {@code connectAccount} runs the same check once it has saved the credential.
     */
    CrmConnectionCheckResponse checkConnection(long companyId);

    void disconnect(long companyId);

    /** The Uysot grants the company asked for; empty when it has none on record (the platform's own token). */
    List<CrmGrant> findGrants(long companyId);
}
