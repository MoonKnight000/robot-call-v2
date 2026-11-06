package uz.murodjon.robotcallv2.user.domain.event;

/**
 * An admin set a new password for a user. Whoever held the old one may still hold a session
 * opened with it, so a synchronous listener ends every session and token of that user.
 */
public record UserPasswordResetByAdminEvent(long companyId, long userId) {
}
