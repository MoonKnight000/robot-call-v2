package uz.murodjon.robotcallv2.user.domain.event;

/** A user was blocked; a synchronous listener ends every session and token they hold. */
public record UserBlockedEvent(long companyId, long userId) {
}
