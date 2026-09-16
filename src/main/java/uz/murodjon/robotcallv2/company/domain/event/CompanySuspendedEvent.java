package uz.murodjon.robotcallv2.company.domain.event;

/**
 * A company was just suspended. Login and refresh already refuse it; a synchronous
 * listener ends the sessions its users still hold.
 */
public record CompanySuspendedEvent(long companyId) {
}
