package uz.murodjon.robotcallv2.partner.application.dto;

import java.time.Instant;

/**
 * The new client and its administrator's activation token. No mail goes out from us: the
 * partner sends the link itself, from its own domain and under its own brand.
 */
public record CreatePartnerClientResponse(long companyId, String email, String inviteToken, Instant expiresAt) {
}
