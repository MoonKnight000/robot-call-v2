package uz.murodjon.robotcallv2.agent.ari;

import uz.murodjon.robotcallv2.agent.audio.LiveAudioMonitor;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;

import java.nio.file.Path;

/** The media half of a call opened by {@link CallMediaFactory#openMediaLegs}. */
record MediaLegs(RtpEndpoint endpoint, String extMediaChannelId, String bridgeId, Path wav,
                 LiveAudioMonitor audioMonitor) {
}
