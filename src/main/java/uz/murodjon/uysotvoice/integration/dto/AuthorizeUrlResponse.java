package uz.murodjon.uysotvoice.integration.dto;

/** {@code GET /api/settings/integrations/uysot/authorize-url} response (§11) — open this in the browser to start the OAuth dance. */
public record AuthorizeUrlResponse(String authorizeUrl) {
}
