package uz.murodjon.robotcallv2.dialer.application.dto;

/**
 * One SIP trunk's calls per second, as a campaign's dialing capacity shows it.
 *
 * @param maxCps          the rate the dialer starts calls over this trunk at
 * @param platformDefault true when the trunk sets none and {@code voice-agent.dialer.default-trunk-cps} applies
 */
public record TrunkCpsRow(long sipTrunkId, String name, int maxCps, boolean platformDefault) {
}
