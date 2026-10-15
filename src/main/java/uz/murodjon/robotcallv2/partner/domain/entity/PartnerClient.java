package uz.murodjon.robotcallv2.partner.domain.entity;

import java.time.Instant;

/** A company a partner brought in; the partner sees it in its client list and brands its panel. */
public record PartnerClient(long partnerCompanyId, long clientCompanyId, Instant createdAt) {
}
