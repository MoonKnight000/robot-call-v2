package uz.murodjon.robotcallv2.campaign.application.port.output;

import uz.murodjon.robotcallv2.campaign.domain.enums.TargetSourceMethod;

import java.net.URI;

/** One request to a company's own target-source API. */
public interface TargetSourceFetcherPort {

    /**
     * @param uri             an address PublicUrlGuard has already screened
     * @param requestBody     sent as JSON when {@code method} is POST, ignored otherwise
     * @param authHeaderValue the decrypted secret, or null when the source sends no header
     * @return the response body, never blank. Raises ExternalServiceException when the
     *         endpoint cannot be reached or answers other than 2xx, empty, or too large
     */
    String fetch(URI uri, TargetSourceMethod method, String requestBody, String authHeaderName, String authHeaderValue);
}
