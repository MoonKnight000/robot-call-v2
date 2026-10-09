package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.TalkActivityTracker;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTalkMetrics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Records a client's call with an operator in stereo — the client on the left, the operator on
 * the right (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.1), as an AI call has the bot on the right.
 *
 * <p>The call's own externalMedia sits in the bridge with both people and so hears them mixed.
 * Once an operator joins, each person is heard through a tap of their own (a snoop on their
 * leg, {@link CallMediaFactory#openTap}) and written into the call's recorder, while the mixed
 * audio stops being recorded. The file, its upload and its row stay those of the call.
 * Each tap also carries a recognizer, so the call is transcribed as it goes
 * ({@link OperatorCallTranscriber}).
 * When the operator part ends, how much each side talked is stored ({@link TalkActivityTracker}).
 *
 * <p>Failing to open the taps never fails the call: it is then recorded mixed, as before.
 */
@Component
class OperatorCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(OperatorCallRecorder.class);

    private final AriClient ariClient;
    private final CallMediaFactory callMediaFactory;
    private final CallSessionRegistry callSessionRegistry;
    private final OperatorCallTranscriber operatorCallTranscriber;
    private final CallAttemptUseCase callAttemptUseCase;

    /** By the client's channel. Guarded by {@code this}, like {@link #clientByOperator}. */
    private final Map<String, StereoTaps> tapsByClient = new HashMap<>();
    private final Map<String, String> clientByOperator = new HashMap<>();

    OperatorCallRecorder(AriClient ariClient, CallMediaFactory callMediaFactory,
                         CallSessionRegistry callSessionRegistry, OperatorCallTranscriber operatorCallTranscriber,
                         CallAttemptUseCase callAttemptUseCase) {
        this.ariClient = ariClient;
        this.callMediaFactory = callMediaFactory;
        this.callSessionRegistry = callSessionRegistry;
        this.operatorCallTranscriber = operatorCallTranscriber;
        this.callAttemptUseCase = callAttemptUseCase;
    }

    /**
     * {@code operatorChannelId} now talks with the client of {@code clientChannelId}. A second
     * operator taking the call over (a transfer) replaces the first one's tap.
     */
    void attach(String clientChannelId, String operatorChannelId) {
        CallSession session = callSessionRegistry.find(clientChannelId);
        ARI current = ariClient.findConnection();
        if (session == null || current == null) {
            return;
        }
        RtpEndpoint recording = session.endpoint();
        StereoTaps existing;
        synchronized (this) {
            existing = tapsByClient.get(clientChannelId);
        }
        // Stopped before the taps start, so no stretch of the client is written twice.
        recording.setRecordingInbound(false);
        // One for the whole call, kept across a transfer like the client's tap.
        TalkActivityTracker activity = existing != null
                ? existing.activity() : new TalkActivityTracker(System.nanoTime());
        AudioListener clientSink = (pcm, length) -> {
            recording.recordCallerSide(pcm, length);
            activity.onClientAudio(pcm, length, System.nanoTime());
        };
        AudioListener operatorSink = (pcm, length) -> {
            recording.recordOtherSide(pcm, length);
            activity.onOperatorAudio(pcm, length, System.nanoTime());
        };
        MediaTap clientTap = existing != null ? existing.clientTap() : null;
        MediaTap operatorTap;
        try {
            if (clientTap == null) {
                clientTap = callMediaFactory.openTap(current, clientChannelId, clientSink);
                transcribe(session, clientTap, clientSink, OperatorCallTranscriber.CLIENT);
            }
            operatorTap = callMediaFactory.openTap(current, operatorChannelId, operatorSink);
            transcribe(session, operatorTap, operatorSink, OperatorCallTranscriber.OPERATOR);
        } catch (Exception e) {
            log.warn("[{}] stereo recording with operator leg {} could not start — recording it mixed: {}",
                    clientChannelId, operatorChannelId, e.getMessage());
            if (existing == null) {
                if (clientTap != null) {
                    callMediaFactory.releaseTap(current, clientTap);
                }
                recording.setRecordingInbound(true);
            }
            return;
        }
        StereoTaps previous;
        synchronized (this) {
            previous = tapsByClient.put(clientChannelId, new StereoTaps(clientTap, operatorTap, operatorChannelId,
                    activity, session.callAttemptId()));
            if (previous != null) {
                clientByOperator.remove(previous.operatorChannelId());
            }
            clientByOperator.put(operatorChannelId, clientChannelId);
        }
        if (previous != null) {
            callMediaFactory.releaseTap(current, previous.operatorTap());
        }
        if (!callSessionRegistry.contains(clientChannelId)) {
            // The client left while the taps were opening, and its teardown found none to close.
            release(clientChannelId);
            return;
        }
        log.info("[{}] recording in stereo with operator leg {}", clientChannelId, operatorChannelId);
    }

    /**
     * A leg ended — the client's or its operator's. The taps are closed and, while the client
     * is still there, the call's own audio is recorded again.
     */
    void release(String channelId) {
        String clientChannelId;
        StereoTaps taps;
        synchronized (this) {
            String byOperator = clientByOperator.remove(channelId);
            clientChannelId = byOperator != null ? byOperator : channelId;
            taps = tapsByClient.remove(clientChannelId);
            if (taps != null) {
                clientByOperator.remove(taps.operatorChannelId());
            }
        }
        if (taps == null) {
            return;
        }
        ARI current = ariClient.findConnection();
        callMediaFactory.releaseTap(current, taps.clientTap());
        callMediaFactory.releaseTap(current, taps.operatorTap());
        CallTalkMetrics metrics = taps.activity().summarize(taps.callId());
        if (metrics != null) {
            callAttemptUseCase.recordTalkMetrics(metrics);
        }
        CallSession session = callSessionRegistry.find(clientChannelId);
        if (session != null) {
            session.endpoint().setRecordingInbound(true);
        }
    }

    /** Puts a recognizer next to the recording on {@code tap}; closed with the tap's endpoint. */
    private void transcribe(CallSession session, MediaTap tap, AudioListener sink, String role) {
        AudioListener recognizer = operatorCallTranscriber.openFor(session, role);
        if (recognizer != null) {
            tap.endpoint().replaceListeners(List.of(sink, recognizer));
        }
    }

    private record StereoTaps(MediaTap clientTap, MediaTap operatorTap, String operatorChannelId,
                              TalkActivityTracker activity, long callId) {
    }
}
