package uz.murodjon.robotcallv2.user.application.service;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Optional;

/**
 * The app_user making the current request, if any (ROADMAP E.1), and what that identity is
 * allowed to do. Permissions are read from the granted authorities rather than from the
 * principal, so a machine caller authenticated by X-Api-Key answers the same questions as a
 * logged-in user.
 */
public interface CurrentUser {

    Optional<Long> id();

    boolean hasPermission(Permission permission);
}
