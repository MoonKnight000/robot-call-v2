package uz.murodjon.robotcallv2.security;

import uz.murodjon.robotcallv2.role.domain.enums.Permission;

import java.util.Optional;

/**
 * The app_user making the current request, if any (ROADMAP E.1), and what that identity is
 * allowed to do. Permissions are read from the granted authorities rather than from the
 * principal, so the answers stay correct for any identity the filter chain sets up.
 */
public interface CurrentUser {

    Optional<Long> id();

    boolean hasPermission(Permission permission);

    /**
     * Who to name in an audit trail or a do-not-call removal: the user's email (or
     * {@code user:<id>}) with their role, an API key's prefix, or {@code "system"} on a
     * thread with no request — the dialer, ARI callbacks, schedulers.
     */
    String actorName();

    /** The client address of the current HTTP request; empty off a request thread. */
    Optional<String> remoteAddress();

    /** The current request's User-Agent, cut to 255 characters; empty off a request thread. */
    Optional<String> userAgent();

    /**
     * Wraps a runnable to run with the current user's security context on another thread
     * (e.g. an asynchronous or virtual thread).
     */
    default Runnable wrap(Runnable action) {
        return action;
    }
}
