package uz.murodjon.uysotvoice.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.AriWSHelper;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.StasisEnd;
import ch.loway.oss.ari4java.generated.models.StasisStart;
import io.netty.channel.EventLoopGroup;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.rtp.*;
import uz.murodjon.uysotvoice.agent.session.CallSession;
import uz.murodjon.uysotvoice.agent.stt.SttProperties;
import uz.murodjon.uysotvoice.agent.stt.SttProvider;
import uz.murodjon.uysotvoice.agent.stt.SttStreamBridge;
import uz.murodjon.uysotvoice.agent.stt.TranscriptListener;
import uz.murodjon.uysotvoice.agent.tts.TtsProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsRouter;
import uz.murodjon.uysotvoice.agent.dialog.CallContext;
import uz.murodjon.uysotvoice.agent.dialog.DialogEngine;
import uz.murodjon.uysotvoice.agent.dialog.DialogProperties;
import uz.murodjon.uysotvoice.agent.vad.SileroVad;
import uz.murodjon.uysotvoice.agent.vad.VadProperties;
import uz.murodjon.uysotvoice.agent.vad.VadStream;
import uz.murodjon.uysotvoice.agent.record.CallFinalizer;
import uz.murodjon.uysotvoice.agent.record.CallRecordService;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.operator.OperatorProperties;
import uz.murodjon.uysotvoice.agent.routing.CallRouteRegistry;
import uz.murodjon.uysotvoice.dialer.CampaignService;
import uz.murodjon.uysotvoice.dialer.DialerState;
import uz.murodjon.uysotvoice.dialer.OutboundCall;
import uz.murodjon.uysotvoice.dialer.OutboundCallRegistry;

import java.time.Instant;
import java.time.LocalDate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Owns the ARI WebSocket connection and call control (PROJECT.md §8).
 *
 * <p>Stage 3 flow: on {@code StasisStart} the caller is answered, an
 * externalMedia channel is created pointing at a fresh RTP listener, both are
 * joined in a mixing bridge, and incoming audio is recorded to a WAV file. On
 * hangup ({@code StasisEnd}) the media resources are torn down.
 */
@Service
public class AriService {

    private static final Logger log = LoggerFactory.getLogger(AriService.class);

    /** Telephone audio is 8 kHz (PROJECT.md §7.1). */
    private static final int SAMPLE_RATE = 8000;
    /** externalMedia channels appear in Stasis with this technology prefix. */
    private static final String EXTERNAL_MEDIA_PREFIX = "UnicastRTP";

    private final AsteriskProperties props;
    private final RtpProperties rtpProps;
    private final RtpPortAllocator portAllocator;
    private final EventLoopGroup rtpEventLoopGroup;
    private final ExecutorService callExecutor;
    private final SttProperties sttProps;
    private final ObjectProvider<SttProvider> sttProviderProvider;
    private final TtsProperties ttsProps;
    private final TtsRouter ttsRouter;
    private final DialogProperties dialogProps;
    private final DialogEngine dialogEngine;
    private final VadProperties vadProps;
    private final ObjectProvider<SileroVad> vadProvider;
    private final CallRecordService callRecordService;
    private final CallFinalizer callFinalizer;
    private final OutboundCallRegistry outboundRegistry;
    private final DialerState dialerState;
    private final CampaignService campaignService;
    private final OperatorProperties operatorProps;
    private final VoiceMetrics metrics;
    private final CallRouteRegistry routeRegistry;

    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

    private volatile ARI ari;

    public AriService(AsteriskProperties props,
                      RtpProperties rtpProps,
                      RtpPortAllocator portAllocator,
                      EventLoopGroup rtpEventLoopGroup,
                      ExecutorService callExecutor,
                      SttProperties sttProps,
                      ObjectProvider<SttProvider> sttProviderProvider,
                      TtsProperties ttsProps,
                      TtsRouter ttsRouter,
                      DialogProperties dialogProps,
                      DialogEngine dialogEngine,
                      VadProperties vadProps,
                      ObjectProvider<SileroVad> vadProvider,
                      CallRecordService callRecordService,
                      CallFinalizer callFinalizer,
                      OutboundCallRegistry outboundRegistry,
                      DialerState dialerState,
                      CampaignService campaignService,
                      OperatorProperties operatorProps,
                      VoiceMetrics metrics,
                      CallRouteRegistry routeRegistry) {
        this.props = props;
        this.rtpProps = rtpProps;
        this.portAllocator = portAllocator;
        this.rtpEventLoopGroup = rtpEventLoopGroup;
        this.callExecutor = callExecutor;
        this.sttProps = sttProps;
        this.sttProviderProvider = sttProviderProvider;
        this.ttsProps = ttsProps;
        this.ttsRouter = ttsRouter;
        this.dialogProps = dialogProps;
        this.dialogEngine = dialogEngine;
        this.vadProps = vadProps;
        this.vadProvider = vadProvider;
        this.callRecordService = callRecordService;
        this.callFinalizer = callFinalizer;
        this.outboundRegistry = outboundRegistry;
        this.dialerState = dialerState;
        this.campaignService = campaignService;
        this.operatorProps = operatorProps;
        this.metrics = metrics;
        this.routeRegistry = routeRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!props.enabled()) {
            log.info("Asterisk ARI disabled (voice-agent.asterisk.enabled=false); not connecting");
            return;
        }
        try {
            // IM_FEELING_LUCKY negotiates the ARI version with Asterisk automatically.
            ari = ARI.build(props.ariUrl(), props.appName(), props.ariUser(), props.ariPassword(),
                    AriVersion.IM_FEELING_LUCKY);
            ari.events().eventWebsocket(props.appName()).execute(new AriWSHelper() {
                @Override
                public void onStasisStart(StasisStart event) {
                    handleStasisStart(event);
                }

                @Override
                public void onStasisEnd(StasisEnd event) {
                    handleStasisEnd(event);
                }
            });
            log.info("Connected to Asterisk ARI at {} as app '{}'", props.ariUrl(), props.appName());
        } catch (Exception e) {
            // Non-fatal: let the app run even if Asterisk is not up yet (dev convenience).
            log.error("Failed to connect to Asterisk ARI at {}: {}", props.ariUrl(), e.getMessage());
        }
    }

    /**
     * Places an outbound call to {@code number} and routes it into the Stasis app.
     *
     * <p>The number decides which PJSIP endpoint carries the call: short internal
     * numbers go to the local test softphone, everything else to the trunk. See
     * {@link #endpointFor(String)}.
     *
     * @return the created channel id
     */
    public String originate(String number) {
        ARI current = requireConnection();
        String callId = "call-" + System.currentTimeMillis();
        String endpoint = endpointFor(number);
        try {
            var request = current.channels()
                    .originate("PJSIP/" + number + "@" + endpoint)
                    .setApp(props.appName())
                    .setAppArgs(callId)
                    .setTimeout(props.answerTimeoutSec());
            if (props.callerId() != null && !props.callerId().isBlank()) {
                request.setCallerId(props.callerId());
            }
            Channel channel = request.execute();
            log.info("Originated call {} to {} via {} -> channel {}", callId, number, endpoint, channel.getId());
            return channel.getId();
        } catch (Exception e) {
            throw new IllegalStateException("Originate to " + number + " via " + endpoint
                    + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Chooses the PJSIP endpoint for {@code number}: the local test softphone when the
     * number looks internal (matches {@code localNumberPattern}), otherwise the trunk.
     * Local routing is skipped entirely when either property is left blank.
     */
    private String endpointFor(String number) {
        String local = props.localEndpoint();
        String pattern = props.localNumberPattern();
        if (local != null && !local.isBlank()
                && pattern != null && !pattern.isBlank()
                && number != null && number.matches(pattern)) {
            return local;
        }
        return props.trunkEndpoint();
    }

    /**
     * Plays an 8 kHz mono WAV file to the caller over RTP (Stage 4).
     *
     * @throws IllegalStateException if the call is not active or the file cannot be read
     */
    public void play(String channelId, Path file) {
        CallSession session = sessions.get(channelId);
        if (session == null) {
            throw new IllegalStateException("No active call for channel " + channelId);
        }
        try {
            WavReader.WavAudio audio = WavReader.read(file);
            if (audio.sampleRate() != SAMPLE_RATE) {
                log.warn("Playback file {} is {} Hz, expected {} Hz — audio may sound wrong",
                        file, audio.sampleRate(), SAMPLE_RATE);
            }
            session.endpoint().playPcm(audio.samples());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to play " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Synthesizes {@code text} in {@code language} (TTS router picks the provider)
     * and streams it to the caller over RTP (Stage 6). A blank language falls back
     * to {@code voice-agent.tts.default-language}.
     *
     * @throws IllegalStateException if the call is not active or TTS is disabled
     */
    public void speak(String channelId, String text, String language) {
        CallSession session = sessions.get(channelId);
        if (session == null) {
            throw new IllegalStateException("No active call for channel " + channelId);
        }
        if (!ttsProps.enabled()) {
            throw new IllegalStateException("TTS is disabled (voice-agent.tts.enabled=false)");
        }
        String lang = (language == null || language.isBlank()) ? ttsProps.defaultLanguage() : language;
        short[] pcm = ttsRouter.synthesize(text, lang);
        log.info("TTS speak [{}] lang={} chars={} -> {} samples", channelId, lang, text.length(), pcm.length);
        session.endpoint().playPcm(pcm);
    }

    private void handleStasisStart(StasisStart event) {
        Channel channel = event.getChannel();
        String channelId = channel.getId();
        String name = channel.getName();
        if (name != null && name.startsWith(EXTERNAL_MEDIA_PREFIX)) {
            // Our own externalMedia channel entering Stasis — not a caller.
            log.debug("Ignoring externalMedia StasisStart for {} ({})", channelId, name);
            return;
        }
        List<String> args = event.getArgs();
        if (args != null && !args.isEmpty() && "operator".equals(args.get(0))) {
            // An operator/queue channel we originated for a transfer (§11.6).
            log.info("StasisStart (operator): channel {} args {}", channelId, args);
            callExecutor.execute(() -> handleOperatorJoin(channel, args));
            return;
        }
        log.info("StasisStart: channel {} ({}) args {}", channelId, name, args);
        callExecutor.execute(() -> withMdc(channelId, () -> setupMedia(channelId)));
    }

    /** Run {@code body} with the call id in the logging context (structured logging §12). */
    private static void withMdc(String channelId, Runnable body) {
        MDC.put("channelId", channelId);
        try {
            body.run();
        } finally {
            MDC.remove("channelId");
        }
    }

    /**
     * Transfer the caller to a human operator (§11.6, Stage 11): originate the
     * operator channel into Stasis, carrying the caller's bridge id in its app args
     * so it joins on answer. Falls back to hangup if operator transfer is off.
     */
    public void transferToOperator(String channelId) {
        CallSession session = sessions.get(channelId);
        if (!operatorProps.enabled() || operatorProps.endpoint() == null || operatorProps.endpoint().isBlank()
                || session == null) {
            log.info("Operator transfer unavailable for {}; hanging up", channelId);
            hangup(channelId);
            return;
        }
        try {
            requireConnection().channels()
                    .originate(operatorProps.endpoint())
                    .setApp(props.appName())
                    .setAppArgs("operator," + session.bridgeId() + "," + channelId)
                    .setTimeout(operatorProps.answerTimeoutSec())
                    .execute();
            log.info("Transferring {} to operator {} (bridge {})",
                    channelId, operatorProps.endpoint(), session.bridgeId());
        } catch (Exception e) {
            log.error("Operator transfer failed for {}: {}", channelId, e.getMessage());
            hangup(channelId);
        }
    }

    private void handleOperatorJoin(Channel operatorChannel, List<String> args) {
        String bridgeId = args.get(1);
        String callerId = args.get(2);
        try {
            ARI current = requireConnection();
            current.channels().answer(operatorChannel.getId()).execute();
            // Remove the AI leg so only caller <-> operator remain in the bridge.
            CallSession caller = sessions.get(callerId);
            if (caller != null) {
                try {
                    current.channels().hangup(caller.extMediaChannelId()).execute();
                } catch (Exception e) {
                    log.debug("extMedia {} already gone: {}", caller.extMediaChannelId(), e.getMessage());
                }
            }
            current.bridges().addChannel(bridgeId, operatorChannel.getId()).execute();
            log.info("Operator {} joined bridge {} for caller {}", operatorChannel.getId(), bridgeId, callerId);
        } catch (Exception e) {
            log.error("Operator join failed for bridge {}: {}", bridgeId, e.getMessage());
            hangup(operatorChannel.getId());
        }
    }

    private void setupMedia(String channelId) {
        ARI current = requireConnection();
        int port = portAllocator.allocate();
        RtpEndpoint endpoint = null;
        try {
            answer(channelId);

            Files.createDirectories(Path.of(rtpProps.recordingDir()));
            Path wav = Path.of(rtpProps.recordingDir(), channelId.replace('/', '_') + ".wav");
            WavRecorder recorder = new WavRecorder(wav, SAMPLE_RATE);

            // Stage 10: outbound campaign call carries its target + real facts; otherwise
            // this is a manual/test call and uses the placeholder target + test context.
            OutboundCall outbound = outboundRegistry.peek(channelId);
            long targetId;
            String language;
            CallContext context;
            if (outbound != null) {
                outboundRegistry.markAnswered(channelId);
                targetId = outbound.targetId();
                language = outbound.language() != null ? outbound.language() : dialogProps.language();
                context = outbound.context();
            } else {
                targetId = callRecordService.manualTargetId();
                language = dialogProps.language();
                context = buildTestContext();
            }

            // Stage 9: open a call_attempt so transcripts/result can be persisted.
            Instant startedAt = Instant.now();
            long attemptId = callRecordService.startAttempt(targetId, channelId, language);

            endpoint = new RtpEndpoint(port, recorder, buildAudioListeners(channelId, attemptId, startedAt));
            endpoint.start(rtpEventLoopGroup);

            // externalMedia: Asterisk sends the caller's audio to our listener (PROJECT.md §8.4).
            Channel extMedia = current.channels()
                    .externalMedia(props.appName(), rtpProps.localIp() + ":" + port, "ulaw")
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute();
            // Learn where to send our TTS without waiting for the caller's audio
            // (a silent inbound leg would otherwise leave us with no peer).
            pointPlaybackAt(current, extMedia.getId(), endpoint);

            Bridge bridge = current.bridges().create().setType("mixing").execute();
            current.bridges().addChannel(bridge.getId(), channelId + "," + extMedia.getId()).execute();

            sessions.put(channelId, new CallSession(channelId, extMedia.getId(), bridge.getId(),
                    port, endpoint, attemptId, startedAt, wav.toString()));
            log.info("Media ready for {}: rtpPort={}, extMedia={}, bridge={}, wav={}, attempt={}",
                    channelId, port, extMedia.getId(), bridge.getId(), wav, attemptId);

            // Stage 12: metrics + sticky-routing registration.
            metrics.callStarted();
            metrics.activeInc();
            routeRegistry.register(channelId);

            // Stage 4 test: auto-play a prepared WAV once media is up, if configured.
            String testFile = rtpProps.testPlaybackFile();
            if (testFile != null && !testFile.isBlank()) {
                play(channelId, Path.of(testFile));
            }

            // Stage 7: start the LLM dialog (bot greets and drives the conversation).
            // Outbound campaign calls always run dialog; inbound/manual only if auto-start.
            if (dialogProps.enabled() && (dialogProps.autoStart() || outbound != null)) {
                dialogEngine.startCall(channelId, endpoint, context, language,
                        () -> hangup(channelId), () -> transferToOperator(channelId), attemptId);
            }
        } catch (Exception e) {
            log.error("Failed to set up media for {}: {}", channelId, e.getMessage(), e);
            if (endpoint != null) {
                endpoint.close();
            }
            portAllocator.release(port);
            hangup(channelId);
        }
    }

    /**
     * Tell {@code endpoint} which socket Asterisk opened for the externalMedia
     * channel, so playback works even before (or without) inbound audio.
     * Asterisk exposes it on the UnicastRTP channel as UNICASTRTP_LOCAL_ADDRESS /
     * UNICASTRTP_LOCAL_PORT. Best-effort: symmetric-RTP discovery remains the
     * fallback if the variables are missing (older Asterisk).
     */
    private void pointPlaybackAt(ARI current, String extMediaChannelId, RtpEndpoint endpoint) {
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

    private List<AudioListener> buildAudioListeners(String channelId, long callAttemptId, Instant startedAt) {
        List<AudioListener> listeners = new ArrayList<>();

        // Barge-in detector (Stage 8): silences the bot when the caller speaks over it.
        if (vadProps.enabled()) {
            SileroVad vad = vadProvider.getIfAvailable();
            if (vad != null && vad.available()) {
                listeners.add(new VadStream(vad, vadProps, channelId,
                        () -> dialogEngine.notifyBargeIn(channelId)));
            }
        }

        // Speech-to-text + dialog (Stages 5/7) + transcript persistence (Stage 9).
        if (sttProps.enabled()) {
            SttProvider stt = sttProviderProvider.getIfAvailable();
            if (stt != null) {
                boolean dialog = dialogProps.enabled() && dialogEngine.available();
                TranscriptListener listener = (text, isFinal, confidence) -> {
                    if (isFinal) {
                        log.info("[{}] FINAL (conf={}): {}", channelId, confidence, text);
                        int offsetMs = (int) (Instant.now().toEpochMilli() - startedAt.toEpochMilli());
                        callRecordService.addTranscript(callAttemptId, "CLIENT", text, null, offsetMs, confidence);
                        if (dialog) {
                            dialogEngine.onClientFinal(channelId, text);
                        }
                    } else {
                        log.debug("[{}] interim: {}", channelId, text);
                    }
                };
                try {
                    listeners.add(new SttStreamBridge(stt, sttProps.google().sampleRate(),
                            channelId, sttProps.defaultLanguage(), listener));
                } catch (Exception e) {
                    log.warn("STT not started for {}: {}", channelId, e.getMessage());
                }
            }
        }
        return listeners;
    }

    private CallContext buildTestContext() {
        DialogProperties.TestContext t = dialogProps.testContext();
        if (t == null) {
            return new CallContext(null, null, null, null, null, null);
        }
        LocalDate due = null;
        if (t.dueDate() != null && !t.dueDate().isBlank()) {
            try {
                due = LocalDate.parse(t.dueDate());
            } catch (Exception e) {
                log.warn("Invalid voice-agent.dialog.test-context.due-date '{}': {}", t.dueDate(), e.getMessage());
            }
        }
        return new CallContext(t.clientName(), t.debtAmount(), t.currency(), due, t.contractNumber(), t.goal());
    }

    private void handleStasisEnd(StasisEnd event) {
        String channelId = event.getChannel().getId();
        // Read the dialog outcome before dropping the session, then tear down.
        Disposition disposition = dialogEngine.disposition(channelId);
        dialogEngine.endCall(channelId);
        CallSession session = sessions.remove(channelId);
        if (session == null) {
            return; // externalMedia channel or already torn down
        }
        log.info("StasisEnd: channel {} — tearing down media", channelId);
        callExecutor.execute(() -> withMdc(channelId, () -> teardown(session, disposition)));
    }

    private void teardown(CallSession session, Disposition disposition) {
        session.endpoint().close(); // finalizes the WAV file
        portAllocator.release(session.rtpPort());
        ARI current = ari;
        if (current != null) {
            try {
                current.channels().hangup(session.extMediaChannelId()).execute();
            } catch (Exception e) {
                log.debug("externalMedia channel {} already gone: {}", session.extMediaChannelId(), e.getMessage());
            }
            try {
                current.bridges().destroy(session.bridgeId()).execute();
            } catch (Exception e) {
                log.debug("Bridge {} already gone: {}", session.bridgeId(), e.getMessage());
            }
        }
        log.info("Torn down call {}", session.channelId());

        // Stage 12: metrics + sticky-routing cleanup.
        metrics.activeDec();
        routeRegistry.unregister(session.channelId());

        // Stage 10: outbound campaign call — release the concurrency slot and update
        // the target (retry/done). clientId feeds the CRM note.
        OutboundCall outbound = outboundRegistry.remove(session.channelId());
        long clientId = 0L;
        if (outbound != null) {
            clientId = outbound.clientId() != null ? outbound.clientId() : 0L;
            dialerState.release();
            campaignService.applyOutcome(outbound.targetId(), disposition);
        }

        // Stage 9: upload recording, summarize, write result + CRM note (WAV now finalized).
        if (session.callAttemptId() != 0) {
            callFinalizer.finalizeCall(session.callAttemptId(), clientId,
                    Path.of(session.wavPath()), session.startedAt(), disposition);
        }
    }

    /** Hang up a channel by id (used by the dialer to reclaim unanswered calls). */
    public void hangupChannel(String channelId) {
        hangup(channelId);
    }

    private void answer(String channelId) {
        try {
            requireConnection().channels().answer(channelId).execute();
            log.info("Answered channel {}", channelId);
        } catch (Exception e) {
            log.warn("Answer failed for channel {}: {}", channelId, e.getMessage());
        }
    }

    private void hangup(String channelId) {
        try {
            requireConnection().channels().hangup(channelId).execute();
            log.info("Hung up channel {}", channelId);
        } catch (Exception e) {
            log.warn("Hangup failed for channel {}: {}", channelId, e.getMessage());
        }
    }

    private ARI requireConnection() {
        ARI current = ari;
        if (current == null) {
            throw new IllegalStateException("ARI is not connected");
        }
        return current;
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(session -> {
            try {
                teardown(session, dialogEngine.disposition(session.channelId()));
            } catch (Exception e) {
                log.warn("Teardown during shutdown failed for {}: {}", session.channelId(), e.getMessage());
            }
        });
        sessions.clear();
        ARI current = ari;
        if (current != null) {
            try {
                current.cleanup();
                log.info("ARI connection cleaned up");
            } catch (Exception e) {
                log.warn("ARI cleanup failed: {}", e.getMessage());
            }
        }
    }
}
