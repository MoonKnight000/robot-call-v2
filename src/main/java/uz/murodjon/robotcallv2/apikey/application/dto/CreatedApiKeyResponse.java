package uz.murodjon.robotcallv2.apikey.application.dto;

/**
 * The one and only time the key itself is returned.
 *
 * <p>Kept apart from {@link ApiKeyRow} so that the secret cannot be served by accident
 * from a listing endpoint: there is no field to leak it through, because only the response
 * to the request that created it has one.
 *
 * @param key the credential, to be stored by the caller now — it is not recoverable
 */
public record CreatedApiKeyResponse(
        ApiKeyRow apiKey,
        String key
) {
}
