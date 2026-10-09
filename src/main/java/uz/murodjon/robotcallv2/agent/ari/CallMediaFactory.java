package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.Channel;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.LiveAudioMonitor;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.WavRecorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The media legs of a call on the Asterisk side and ours: an RTP listener with its recorder,
 * the externalMedia channel feeding it, and the mixing bridge they sit in — opened, pointed
 * at each other, and released again. Also plays a one-off prompt to a single leg.
 */
@Component
class CallMediaFactory {

    private static final Logger log = LoggerFactory.getLogger(CallMediaFactory.class);

    /** Telephone audio is 8 kHz (PROJECT.md §7.1). */
    static final int SAMPLE_RATE = 8000;

    private final AriClient ariClient;
    private final AsteriskProperties asteriskProperties;
    private final RtpProperties rtpProperties;
    private final RtpPortAllocator portAllocator;
    private final EventLoopGroup rtpEventLoopGroup;

    CallMediaFactory(AriClient ariClient, AsteriskProperties asteriskProperties, RtpProperties rtpProperties,
                     RtpPortAllocator portAllocator, EventLoopGroup rtpEventLoopGroup) {
        this.ariClient = ariClient;
        this.asteriskProperties = asteriskProperties;
        this.rtpProperties = rtpProperties;
        this.portAllocator = portAllocator;
        this.rtpEventLoopGroup = rtpEventLoopGroup;
    }

    /** The media half of a call: RTP listener and recorder, externalMedia, and the mixing bridge they sit in. */
    MediaLegs openMediaLegs(ARI current, String channelId, int port) throws Exception {
        Files.createDirectories(Path.of(rtpProperties.recordingDir()));
        Path wav = Path.of(rtpProperties.recordingDir(), channelId.replace('/', '_') + ".wav");
        LiveAudioMonitor audioMonitor = new LiveAudioMonitor(rtpEventLoopGroup);
        RtpEndpoint endpoint = new RtpEndpoint(channelId, port, rtpProperties.codec(),
                new WavRecorder(wav, SAMPLE_RATE, rtpProperties.recordingMode()),
                List.<AudioListener>of(audioMonitor), audioMonitor::onBotAudio, rtpProperties.receiveQueueCapacity(),
                rtpProperties.maxQueuedPlaybackSeconds());
        String extMediaId = null;
        String bridgeId = null;
        try {
            endpoint.bind(rtpEventLoopGroup);
            Channel extMedia = current.channels()
                    .externalMedia(asteriskProperties.appName(), rtpProperties.localIp() + ":" + port,
                            rtpProperties.codec().asteriskFormat())
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute();
            extMediaId = extMedia.getId();
            pointPlaybackAt(current, extMediaId, endpoint);
            Bridge bridge = current.bridges().create().setType("mixing").execute();
            bridgeId = bridge.getId();
            current.bridges().addChannel(bridgeId, extMediaId).execute();
            return new MediaLegs(endpoint, extMediaId, bridgeId, wav, audioMonitor);
        } catch (Exception e) {
            endpoint.close();
            if (extMediaId != null) {
                ariClient.hangup(extMediaId);
            }
            // A bridge created before the failure stayed in Asterisk until it restarted.
            if (bridgeId != null) {
                ariClient.destroyBridge(bridgeId);
            }
            throw e;
        }
    }

    void releaseMediaLegs(ARI current, MediaLegs legs) {
        legs.endpoint().close();
        try {
            current.channels().hangup(legs.extMediaChannelId()).execute();
        } catch (Exception e) {
            log.debug("externalMedia {} already gone: {}", legs.extMediaChannelId(), e.getMessage());
        }
        ariClient.destroyBridge(legs.bridgeId());
    }

    /**
     * What one leg's own person says, and nothing else: a snoop on {@code channelId} hearing
     * its inbound direction, in a bridge of its own with an externalMedia that streams it to
     * {@code sink}. The audio reaches the sink untouched — no noise cancellation — because it
     * is recorded.
     */
    MediaTap openTap(ARI current, String channelId, AudioListener sink) throws Exception {
        int port = portAllocator.allocate();
        RtpEndpoint endpoint = new RtpEndpoint(channelId + "-tap", port, rtpProperties.codec(), null, List.of(sink),
                rtpProperties.receiveQueueCapacity(), rtpProperties.maxQueuedPlaybackSeconds());
        endpoint.setNoiseCancellation(false, null);
        String snoopId = null;
        String extMediaId = null;
        String bridgeId = null;
        try {
            endpoint.bind(rtpEventLoopGroup);
            extMediaId = current.channels()
                    .externalMedia(asteriskProperties.appName(), rtpProperties.localIp() + ":" + port,
                            rtpProperties.codec().asteriskFormat())
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute().getId();
            snoopId = ariClient.snoop(channelId, "in", "none");
            bridgeId = current.bridges().create().setType("mixing").execute().getId();
            current.bridges().addChannel(bridgeId, extMediaId).execute();
            current.bridges().addChannel(bridgeId, snoopId).execute();
            return new MediaTap(snoopId, extMediaId, bridgeId, endpoint, port);
        } catch (Exception e) {
            releaseTap(current, new MediaTap(snoopId, extMediaId, bridgeId, endpoint, port));
            throw e;
        }
    }

    /** Asterisk's side first, then ours, then the port — as a call's teardown does. */
    void releaseTap(ARI current, MediaTap tap) {
        if (current != null) {
            // The snoop is usually gone already: Asterisk ends it with the leg it listened to.
            for (String channelId : new String[]{tap.snoopChannelId(), tap.extMediaChannelId()}) {
                if (channelId == null) {
                    continue;
                }
                try {
                    current.channels().hangup(channelId).execute();
                } catch (Exception e) {
                    log.debug("Tap channel {} already gone: {}", channelId, e.getMessage());
                }
            }
            if (tap.bridgeId() != null) {
                ariClient.destroyBridge(tap.bridgeId());
            }
        }
        tap.endpoint().close();
        portAllocator.release(tap.port());
    }

    void pointPlaybackAt(ARI current, String extMediaChannelId, RtpEndpoint endpoint) {
        try {
            String host = current.channels().getChannelVar(extMediaChannelId, "UNICASTRTP_LOCAL_ADDRESS")
                    .execute().getValue();
            String port = current.channels().getChannelVar(extMediaChannelId, "UNICASTRTP_LOCAL_PORT")
                    .execute().getValue();
            if (host == null || host.isBlank() || port == null || port.isBlank()) {
                log.warn("externalMedia {} did not report UNICASTRTP_LOCAL_ADDRESS/PORT; "
                        + "playback will wait for inbound RTP", extMediaChannelId);
                return;
            }
            endpoint.setRemote(new java.net.InetSocketAddress(host.trim(), Integer.parseInt(port.trim())));
        } catch (Exception e) {
            log.warn("Could not read UNICASTRTP_LOCAL_ADDRESS/PORT for {}: {}", extMediaChannelId, e.getMessage());
        }
    }

    /**
     * Plays {@code pcm} to one channel alone — the operator's whisper, a forward's "press 1"
     * — through a bridge of its own, and returns once it has been heard. The channel is left
     * in no bridge, ready to be joined wherever it goes next.
     */
    void playToChannel(String channelId, short[] pcm) {
        if (pcm == null || pcm.length == 0) {
            return;
        }
        ARI current = ariClient.requireConnection();
        int port = -1;
        Path scratch = null;
        RtpEndpoint endpoint = null;
        String extMediaId = null;
        String bridgeId = null;
        try {
            port = portAllocator.allocate();
            Files.createDirectories(Path.of(rtpProperties.recordingDir()));
            scratch = Path.of(rtpProperties.recordingDir(), channelId.replace('/', '_') + "-prompt-" + port + ".wav");
            endpoint = new RtpEndpoint(channelId + "-prompt", port, rtpProperties.codec(),
                    new WavRecorder(scratch, SAMPLE_RATE, rtpProperties.recordingMode()), List.of(),
                    rtpProperties.receiveQueueCapacity(), rtpProperties.maxQueuedPlaybackSeconds());
            endpoint.bind(rtpEventLoopGroup);
            Channel extMedia = current.channels()
                    .externalMedia(asteriskProperties.appName(), rtpProperties.localIp() + ":" + port,
                            rtpProperties.codec().asteriskFormat())
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute();
            extMediaId = extMedia.getId();
            pointPlaybackAt(current, extMediaId, endpoint);
            bridgeId = current.bridges().create().setType("mixing").execute().getId();
            current.bridges().addChannel(bridgeId, extMediaId).execute();
            current.bridges().addChannel(bridgeId, channelId).execute();
            endpoint.playPcm(pcm);
            awaitPlaybackDone(endpoint, pcm.length);
            current.bridges().removeChannel(bridgeId, channelId).execute();
        } catch (Exception e) {
            log.warn("[{}] prompt to this leg alone failed: {}", channelId, e.getMessage());
        } finally {
            if (extMediaId != null) {
                try {
                    current.channels().hangup(extMediaId).execute();
                } catch (Exception e) {
                    log.debug("prompt externalMedia {} already gone: {}", extMediaId, e.getMessage());
                }
            }
            if (bridgeId != null) {
                ariClient.destroyBridge(bridgeId);
            }
            if (endpoint != null) {
                endpoint.close();
            }
            portAllocator.release(port);
            if (scratch != null) {
                try {
                    Files.deleteIfExists(scratch);
                } catch (IOException e) {
                    log.debug("Could not delete {}: {}", scratch, e.getMessage());
                }
            }
        }
    }

    /** Waits, up to {@code maxMillis}, for {@code endpoint} to finish what it is playing. */
    void awaitSilence(RtpEndpoint endpoint, long maxMillis) {
        long deadline = System.currentTimeMillis() + maxMillis;
        while (endpoint.isPlaying() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(asteriskProperties.playbackPollMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void awaitPlaybackDone(RtpEndpoint endpoint, int sampleCount) {
        long budgetMs = sampleCount * 1000L / SAMPLE_RATE + asteriskProperties.playbackGraceMs();
        long deadline = System.currentTimeMillis() + budgetMs;
        while (endpoint.isPlaying() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(asteriskProperties.playbackPollMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
