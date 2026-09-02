package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.live.application.service.LiveBroadcastService;
import uz.murodjon.robotcallv2.live.domain.entity.AudioLevelEvent;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

/**
 * Computes a per-frame RMS level and publishes it for the live waveform (§11.8).
 * Throttled: the RTP consumer delivers a frame every 20ms, far more often than a
 * waveform UI can usefully redraw, and every publish fans out to every connected
 * SSE client.
 */
public class AudioLevelListener implements AudioListener {

    /** Caps publishing to roughly this many events per second per call. */
    private static final long MIN_INTERVAL_NANOS = 200_000_000L; // 5/sec

    private final String channelId;
    private final LiveBroadcastService broadcast;
    private volatile long lastPublishNanos = 0;

    public AudioLevelListener(String channelId, LiveBroadcastService broadcast) {
        this.channelId = channelId;
        this.broadcast = broadcast;
    }

    @Override
    public void onAudio(short[] pcm, int length) {
        long now = System.nanoTime();
        if (length <= 0 || now - lastPublishNanos < MIN_INTERVAL_NANOS) {
            return;
        }
        lastPublishNanos = now;

        double sumSquares = 0;
        for (int i = 0; i < length; i++) {
            sumSquares += (double) pcm[i] * pcm[i];
        }
        double rms = Math.sqrt(sumSquares / length);
        float level = (float) Math.min(1.0, rms / Short.MAX_VALUE);

        broadcast.publish(LiveEventType.AUDIO_LEVEL, new AudioLevelEvent(channelId, level));
    }
}

