package uz.murodjon.robotcallv2.company.domain.event;

/**
 * The platform switched a company's features on or off. The permissions its users hold are
 * already inside their tokens, so a synchronous listener ends their sessions to make the
 * next login or refresh carry the new ones.
 */
public record CompanyFeaturesChangedEvent(long companyId) {
}
