package uz.murodjon.robotcallv2.shared.stream;

import java.io.IOException;

/**
 * Where one push subscription's events go, whatever carries them to the client — the
 * presentation layer adapts its transport (an SSE response) to this, so the services that
 * publish never see the transport. Called from the subscription's own writer thread only.
 */
public interface EventSink {

    /** One named event; the payload is serialised by the transport. */
    void send(String eventName, Object payload) throws IOException;

    /** A keep-alive that carries no event, so an idle connection is not taken for a dead one. */
    void sendHeartbeat() throws IOException;
}
