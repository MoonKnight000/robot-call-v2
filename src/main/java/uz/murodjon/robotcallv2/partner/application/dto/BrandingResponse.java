package uz.murodjon.robotcallv2.partner.application.dto;

/**
 * The brand a panel shows: the partner's when the company is (or belongs to) a partner. The
 * envelope's {@code data} is null when our own brand applies.
 */
public record BrandingResponse(
        String productName,
        String logoUrl,
        String primaryColor,
        String supportEmail,
        String supportPhone
) {
}
