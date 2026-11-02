package uz.murodjon.robotcallv2.sms.domain.entity;

import java.time.Instant;

/**
 * A company's own Eskiz account.
 *
 * @param email       the account login
 * @param password    the account password
 * @param sender      the sender name shown to the recipient; null — the platform default
 * @param connectedAt when the account was last connected
 */
public record EskizConnection(String email, String password, String sender, Instant connectedAt) {
}
