package uz.murodjon.uysotvoice.apikey.dto;

/**
 * The raw key is present exactly once, on creation — it is never stored (only its
 * hash is, see {@code ApiKeyEntity#keyHash}) and never returned again afterward.
 */
public record CreateApiKeyResponse(
        ApiKey key,
        String rawKey
) {
}
