package uz.murodjon.robotcallv2.billing.domain.entity;

/**
 * A company's concurrent dialer calls right now, against what it may hold.
 *
 * @param active calls the dialer is holding for the company at this moment
 * @param limit  how many it may hold at once
 */
public record ChannelUsage(int active, int limit) {
}
