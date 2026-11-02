package uz.murodjon.robotcallv2.sms.application.dto;

import uz.murodjon.robotcallv2.sms.domain.entity.EskizConnection;

import java.time.Instant;

/**
 * The company's Eskiz account as the settings screen sees it; the password is never returned.
 *
 * @param connected whether the company has connected its own account
 */
public record EskizConnectionRow(boolean connected, String email, String sender, Instant connectedAt) {

    public static EskizConnectionRow notConnected() {
        return new EskizConnectionRow(false, null, null, null);
    }

    public static EskizConnectionRow of(EskizConnection connection) {
        return new EskizConnectionRow(true, connection.email(), connection.sender(), connection.connectedAt());
    }
}
