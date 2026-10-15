package uz.murodjon.robotcallv2.partner.domain.entity;

import java.time.Instant;

/**
 * A partner's brand — what its clients see instead of ours: the product name on the login page
 * and in the panel, the logo, the accent colour and where to ask for help.
 *
 * @param companyId    the partner company; the storage layer's on a write
 * @param productName  the product name shown to the partner's clients
 * @param domain       the partner's console host ({@code app.partner.uz}), lower-cased; null when
 *                     the partner's clients use our host
 * @param logoUrl      https address of the logo
 * @param primaryColor accent colour, {@code #RRGGBB}
 * @param supportEmail where the partner's clients ask for help
 * @param supportPhone the partner's support line
 * @param updatedAt    when the brand last changed; the storage layer's on a write
 */
public record PartnerBranding(
        long companyId,
        String productName,
        String domain,
        String logoUrl,
        String primaryColor,
        String supportEmail,
        String supportPhone,
        Instant updatedAt
) {
}
