package uz.murodjon.uysotvoice.campaign.dto;

/**
 * A target ready to be inserted.
 *
 * @param line        1-based line number in the file, so an error can be found
 * @param clientId    CRM client id
 * @param phone       number to dial (validated on insert)
 * @param language    per-target language override, or null for the campaign default
 * @param contextJson debtor facts as {@code context_data} JSON
 */
public record ParsedTarget(int line, long clientId, String phone, String language, String contextJson) {
}
