package uz.murodjon.robotcallv2.partner.application.dto;

import uz.murodjon.robotcallv2.company.domain.enums.CompanyStatus;

import java.time.Instant;

/** One row of a partner's client list. {@code joinedAt} is when the partner brought the client in. */
public record PartnerClientRow(long companyId, String name, CompanyStatus status, Instant joinedAt) {
}
