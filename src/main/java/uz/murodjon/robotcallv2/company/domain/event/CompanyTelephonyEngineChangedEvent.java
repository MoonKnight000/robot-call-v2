package uz.murodjon.robotcallv2.company.domain.event;

/**
 * The platform moved a company to another telephony engine. Asterisk registers only the
 * trunks of its own companies, so its generated trunk config is rewritten on this.
 */
public record CompanyTelephonyEngineChangedEvent(long companyId) {
}
