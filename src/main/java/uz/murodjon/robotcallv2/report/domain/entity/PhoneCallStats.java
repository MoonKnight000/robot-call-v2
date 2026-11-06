package uz.murodjon.robotcallv2.report.domain.entity;

import java.time.Instant;

/**
 * How often a number was called and when last — the contacts table's "calls" and "last call" columns.
 *
 * @param lastCallAt start of the newest call, {@code null} when none of the calls started
 */
public record PhoneCallStats(long callCount, Instant lastCallAt) {
}
