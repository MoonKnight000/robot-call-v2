package uz.murodjon.robotcallv2.auth.application.port.input;

import uz.murodjon.robotcallv2.auth.application.dto.AuthenticatedUser;
import uz.murodjon.robotcallv2.auth.application.dto.StreamTicketResponse;

/** What the security filter chain asks of a single-use stream ticket. */
public interface StreamTicketUseCase {

    /**
     * Spends {@code ticket}. Null when it is unknown, already used, expired, its user has
     * been revoked since, or the ticket store cannot be reached.
     */
    AuthenticatedUser consume(String ticket);

    /**
     * A fresh ticket for {@code principal} — for a request whose own ticket this instance
     * spent and that is now replayed on the instance holding its call (CallOwnerProxyFilter).
     */
    StreamTicketResponse create(AuthenticatedUser principal);
}
