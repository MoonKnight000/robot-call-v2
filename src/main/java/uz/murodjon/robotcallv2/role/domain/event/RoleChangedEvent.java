package uz.murodjon.robotcallv2.role.domain.event;

import java.util.List;

/**
 * A role's permissions were edited. The old set still travels inside every access token
 * its holders carry, so a synchronous listener makes {@code userIds} — everyone holding the
 * role at the moment of the edit — log in again.
 */
public record RoleChangedEvent(long companyId, long roleId, List<Long> userIds) {

    public RoleChangedEvent {
        userIds = List.copyOf(userIds);
    }
}
