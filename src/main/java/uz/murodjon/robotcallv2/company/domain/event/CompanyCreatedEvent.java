package uz.murodjon.robotcallv2.company.domain.event;

/**
 * A new tenant exists. Published synchronously inside {@code CompanyService.create}, so a
 * listener's work (the company's system roles) is done before the call returns — in the
 * same thread and transaction, exactly as if it were called in place.
 */
public record CompanyCreatedEvent(long companyId) {
}
