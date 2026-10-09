package uz.murodjon.robotcallv2.agent.ari;

import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;

/** One direction of one leg, heard through a snoop: opened by {@link CallMediaFactory#openTap}. */
record MediaTap(String snoopChannelId, String extMediaChannelId, String bridgeId, RtpEndpoint endpoint, int port) {
}
