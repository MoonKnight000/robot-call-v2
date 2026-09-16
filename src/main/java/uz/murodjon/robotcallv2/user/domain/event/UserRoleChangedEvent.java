package uz.murodjon.robotcallv2.user.domain.event;

/**
 * A user was given another role. Their old permissions are still inside the access token
 * they hold, so a synchronous listener ends every session and token of theirs.
 */
public record UserRoleChangedEvent(long companyId, long userId) {
}
