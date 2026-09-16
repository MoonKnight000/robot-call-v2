package uz.murodjon.robotcallv2.shared.api;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An audio stream written to the client as it is produced — a live call being listened to.
 * The application layer hands one of these out; the controller adapts it to the web
 * framework's streaming body, so no HTTP type reaches a service.
 */
@FunctionalInterface
public interface AudioStreamWriter {

    void writeTo(OutputStream out) throws IOException;
}
