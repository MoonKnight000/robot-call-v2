package uz.murodjon.robotcallv2.auth.application.port.output;

import java.time.Duration;

/** Short-lived, single-use stream tickets, shared by every instance. */
public interface StreamTicketStorePort {

    /**
     * @return false when the store could not be reached — the ticket does not exist and must
     *         not be handed out
     */
    boolean save(String ticketHash, String accessToken, Duration ttl);

    /**
     * Reads and removes the ticket in one step, so two requests racing with the same ticket
     * cannot both get it.
     *
     * @return the access token the ticket stands for; null when it is unknown, already used,
     *         expired, or the store could not be reached
     */
    String consume(String ticketHash);
}
