package uz.murodjon.uysotvoice.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.AriWSHelper;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelDestroyed;
import ch.loway.oss.ari4java.generated.models.ChannelHangupRequest;
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

import uz.murodjon.uysotvoice.agent.audio.AnsweringMachineDetector;
import uz.murodjon.uysotvoice.agent.audio.AudioLevelListener;
import uz.murodjon.uysotvoice.agent.audio.AudioListener;
import uz.murodjon.uysotvoice.agent.audio.SpeechGate;
import uz.murodjon.uysotvoice.agent.dialog.CallContext;
import uz.murodjon.uysotvoice.agent.dialog.DialogEngine;
import uz.murodjon.uysotvoice.agent.dialog.DialogOutcome;
import uz.murodjon.uysotvoice.agent.dialog.DialogProperties;
import uz.murodjon.uysotvoice.agent.dialog.LiveDialogSnapshot;
import uz.murodjon.uysotvoice.agent.dialog.TestContextProperties;
import uz.murodjon.uysotvoice.agent.metrics.VoiceMetrics;
import uz.murodjon.uysotvoice.agent.routing.CallRouteRegistry;
import uz.murodjon.uysotvoice.agent.rtp.RtpEndpoint;
import uz.murodjon.uysotvoice.agent.rtp.RtpPortAllocator;
import uz.murodjon.uysotvoice.agent.rtp.RtpProperties;
import uz.murodjon.uysotvoice.agent.rtp.WavAudio;
import uz.murodjon.uysotvoice.agent.rtp.WavReader;
import uz.murodjon.uysotvoice.agent.rtp.WavRecorder;
import uz.murodjon.uysotvoice.agent.session.CallSession;
import uz.murodjon.uysotvoice.agent.stt.SttProperties;
import uz.murodjon.uysotvoice.agent.stt.SttProvider;
import uz.murodjon.uysotvoice.agent.stt.SttStreamBridge;
import uz.murodjon.uysotvoice.agent.stt.TranscriptListener;
import uz.murodjon.uysotvoice.agent.stt.VadGatingProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsProperties;
import uz.murodjon.uysotvoice.agent.tts.TtsRouter;
import uz.murodjon.uysotvoice.agent.vad.AmdProperties;
import uz.murodjon.uysotvoice.agent.vad.SileroVad;
import uz.murodjon.uysotvoice.agent.vad.VadProperties;
import uz.murodjon.uysotvoice.agent.vad.VadStream;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.call.dto.CallOriginateResponse;
import uz.murodjon.uysotvoice.call.dto.LiveCallRow;
import uz.murodjon.uysotvoice.call.dto.PlayResponse;
import uz.murodjon.uysotvoice.call.dto.SayResponse;
import uz.murodjon.uysotvoice.callrecord.service.CallFinalizer;
import uz.murodjon.uysotvoice.callrecord.service.CallRecordService;
import uz.murodjon.uysotvoice.campaign.dto.CampaignRow;
import uz.murodjon.uysotvoice.campaign.service.CampaignService;
import uz.murodjon.uysotvoice.dialer.dto.OutboundCall;
import uz.murodjon.uysotvoice.dialer.service.DialerState;
import uz.murodjon.uysotvoice.dialer.service.OutboundCallRegistry;
import uz.murodjon.uysotvoice.donotcall.repository.DoNotCallRepository;
import uz.murodjon.uysotvoice.live.config.LiveProperties;
import uz.murodjon.uysotvoice.live.service.LiveBroadcastService;
import uz.murodjon.uysotvoice.operator.config.OperatorProperties;
import uz.murodjon.uysotvoice.shared.dialog.Disposition;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ExternalServiceException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.shared.util.PhoneNumbers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
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
    /** Enough for any realistic prompt; stops one request from running up a TTS bill. */
    private static final int MAX_SAY_CHARS = 1000;

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
    private final DoNotCallRepository doNotCallRepository;
    private final AuditService audit;
    private final LiveBroadcastService broadcast;
    private final LiveProperties liveProps;

    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

    /**
     * Q.850 hangup cause per channel, as Asterisk reports it (§8.6). Kept alongside the
     * sessions rather than inside them because the channels that need it most never got a
     * session: an originate that was never answered produces no StasisStart at all, and
     * its cause code is the only thing that distinguishes "line was busy" from "number
     * does not exist".
     */
    private final Map<String, Integer> hangupCauses = new ConcurrentHashMap<>();

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
                      CallRouteRegistry routeRegistry,
                      DoNotCallRepository doNotCallRepository,
                      AuditService audit,
                      LiveBroadcastService broadcast,
                      LiveProperties liveProps) {
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
        this.doNotCallRepository = doNotCallRepository;
        this.audit = audit;
        this.broadcast = broadcast;
        this.liveProps = liveProps;
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

                @Override
                public void onChannelHangupRequest(ChannelHangupRequest event) {
                    // Usually arrives before StasisEnd (the far end sent BYE), which is
                    // what makes the cause available in time to shape the disposition.
                    rememberCause(event.getChannel(), event.getCause());
                }

                @Override
                public void onChannelDestroyed(ChannelDestroyed event) {
                    handleChannelDestroyed(event);
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
    public String originate(String rawNumber) {
        // Both callers (the REST API and the dialer's campaign targets) supply
        // untrusted text that lands inside the dial string, so validate here — one
        // place covers every dial path (§A3).
        String number = PhoneNumbers.require(rawNumber);
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
            throw new ExternalServiceException("asterisk", "Originate to " + number + " via " + endpoint
                    + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * As {@link #originate(String)}, for a manual/test call placed through the REST API —
     * also records the audit entry, since a manual call to a real subscriber is exactly the
     * action someone will later need to account for (§11).
     */
    public CallOriginateResponse originateManualCall(String number) {
        String channelId = originate(number);
        audit.record("CALL_ORIGINATE_MANUAL", "call", channelId, number);
        return new CallOriginateResponse(number, channelId);
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
            throw new ConflictException("No active call for channel " + channelId);
        }
        try {
            WavAudio audio = WavReader.read(file);
            if (audio.sampleRate() != SAMPLE_RATE) {
                log.warn("Playback file {} is {} Hz, expected {} Hz — audio may sound wrong",
                        file, audio.sampleRate(), SAMPLE_RATE);
            }
            session.endpoint().playPcm(audio.samples());
        } catch (IOException e) {
            throw new ExternalServiceException("asterisk", "Failed to play " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * As {@link #play(String, Path)}, for the REST API: {@code file} is a name inside the
     * recording directory rather than a full path.
     */
    public PlayResponse playRecording(String channelId, String file) {
        Path resolved = resolveInRecordingDir(file);
        play(channelId, resolved);
        return new PlayResponse(channelId, resolved.toString(), "playing");
    }

    /**
     * Resolve {@code file} inside the recording directory. Without this an API caller
     * could hand any absolute path (or one containing {@code ../}) to the WAV reader
     * and have the server read it aloud down the phone.
     */
    private Path resolveInRecordingDir(String file) {
        if (file == null || file.isBlank()) {
            throw new ValidationException("file must not be blank");
        }
        Path base = Path.of(rtpProps.recordingDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(file).normalize();
        if (!resolved.startsWith(base)) {
            throw new ValidationException("file must be inside " + base);
        }
        try {
            // Resolves symlinks too — a link inside the directory must not escape it.
            Path real = resolved.toRealPath();
            if (!real.startsWith(base.toRealPath())) {
                throw new ValidationException("file must be inside " + base);
            }
            return real;
        } catch (IOException e) {
            throw new ValidationException("No such playback file: " + file);
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
        speak(channelId, text, language, null);
    }

    /**
     * As {@link #speak(String, String, String)}, with an explicit voice id from
     * {@code voice-agent.tts.catalog} — how a voice is auditioned before a campaign is
     * created with it.
     */
    public void speak(String channelId, String text, String language, String ttsVoice) {
        CallSession session = sessions.get(channelId);
        if (session == null) {
            throw new ConflictException("No active call for channel " + channelId);
        }
        if (!ttsProps.enabled()) {
            throw new ConflictException("TTS is disabled (voice-agent.tts.enabled=false)");
        }
        String lang = (language == null || language.isBlank()) ? ttsProps.defaultLanguage() : language;
        short[] pcm = ttsRouter.synthesize(text, lang, ttsVoice);
        log.info("TTS speak [{}] lang={} voice={} chars={} -> {} samples",
                channelId, lang, ttsVoice != null ? ttsVoice : "default", text.length(), pcm.length);
        session.endpoint().playPcm(pcm);
    }

    /**
     * As {@link #speak(String, String, String, String)}, for the REST API — validates
     * {@code text} first, so an audition request never reaches the TTS provider with
     * something that would either fail oddly or run up an unbounded bill.
     */
    public SayResponse say(String channelId, String text, String language, String voice) {
        if (text.isBlank()) {
            throw new ValidationException("text must not be blank");
        }
        if (text.length() > MAX_SAY_CHARS) {
            throw new ValidationException("text is longer than " + MAX_SAY_CHARS + " characters");
        }
        speak(channelId, text, language, voice);
        return new SayResponse(channelId, "speaking");
    }

    /**
     * Every call still in conversation, for {@code GET /api/calls/live} (§10.2/§10.3
     * UI-DESIGN.md "Jonli qo'ng'iroqlar"). Joins the dialog engine's live sessions with
     * the outbound registry to add the campaign and phone number — a manual/test call
     * has neither, since {@link OutboundCallRegistry#peek} only tracks calls the dialer
     * or {@code POST /api/calls} originated.
     */
    public List<LiveCallRow> liveCalls() {
        return dialogEngine.liveDialogs().stream()
                .map(this::toLiveCallRow)
                .toList();
    }

    private LiveCallRow toLiveCallRow(LiveDialogSnapshot s) {
        OutboundCall outbound = outboundRegistry.peek(s.channelId());
        Long campaignId = outbound != null ? outbound.campaignId() : null;
        String phone = outbound != null ? outbound.phone() : null;
        String campaignName = null;
        if (campaignId != null) {
            CampaignRow campaign = campaignService.getCampaign(campaignId);
            campaignName = campaign != null ? campaign.name() : null;
        }
        return new LiveCallRow(s.channelId(), phone, s.clientName(), campaignId, campaignName,
                s.language(), s.startedAt(), s.dialogState());
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
        long attemptId = 0;
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
            String ttsVoice;
            CallContext context;
            if (outbound != null) {
                outboundRegistry.markAnswered(channelId);
                targetId = outbound.targetId();
                language = outbound.language() != null ? outbound.language() : dialogProps.language();
                // A campaign that chose no voice keeps the configured routing rather
                // than inheriting the manual-call voice (§2.5).
                ttsVoice = outbound.ttsVoice();
                context = outbound.context();
            } else {
                targetId = callRecordService.manualTargetId();
                language = dialogProps.language();
                ttsVoice = dialogProps.ttsVoice();
                context = buildTestContext();
            }

            // Stage 9: open a call_attempt so transcripts/result can be persisted.
            Instant startedAt = Instant.now();
            attemptId = callRecordService.startAttempt(targetId, channelId, language);

            endpoint = new RtpEndpoint(port, recorder,
                    buildAudioListeners(channelId, attemptId, startedAt, language));
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
                dialogEngine.startCall(channelId, endpoint, context, language, ttsVoice,
                        () -> hangup(channelId), () -> transferToOperator(channelId), attemptId);
            }
        } catch (Exception e) {
            log.error("Failed to set up media for {}: {}", channelId, e.getMessage(), e);
            // Keep the reason on the attempt row: a call that died in media setup
            // otherwise looks identical to one the client never answered.
            callRecordService.recordError(attemptId, e.toString());
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

    private List<AudioListener> buildAudioListeners(String channelId, long callAttemptId, Instant startedAt,
                                                    String language) {
        List<AudioListener> listeners = new ArrayList<>();

        // Live waveform (§11.8): off by default, since it runs on the RTP consumer
        // thread of every active call regardless of whether any UI is watching.
        if (liveProps.audioLevelEnabled()) {
            listeners.add(new AudioLevelListener(channelId, broadcast));
        }

        // Barge-in detector (Stage 8): silences the bot when the caller speaks over it.
        // The same VAD scores also drive the STT gate below, which is why this listener
        // must stay ahead of the STT bridge in the list — they run in order on the RTP
        // consumer thread, and the gate has to see a frame before the bridge asks about it.
        SpeechGate speechGate = null;
        if (vadProps.enabled()) {
            SileroVad vad = vadProvider.getIfAvailable();
            if (vad != null && vad.available()) {
                VadGatingProperties gating = sttProps.vadGating();
                if (gating != null && gating.enabled() && sttProps.enabled()) {
                    speechGate = new SpeechGate(SAMPLE_RATE, gating.preRollMs(), gating.postRollMs());
                }
                listeners.add(new VadStream(vad, vadProps, channelId,
                        () -> dialogEngine.notifyBargeIn(channelId), speechGate,
                        buildAmd(channelId)));
            } else if (sttProps.vadGating() != null && sttProps.vadGating().enabled()) {
                log.debug("[{}] STT gating requested but VAD is unavailable — streaming all audio", channelId);
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
                    // Rate comes from the provider that is actually running — the
                    // Google/Yandex settings are independent (§C3).
                    //
                    // Recognize in the language THIS call speaks, not the global default:
                    // a ru-RU campaign target was previously transcribed as Uzbek, which
                    // yields plausible-looking nonsense the dialog then answers (§3.1).
                    String sttLanguage = (language == null || language.isBlank())
                            ? sttProps.defaultLanguage() : language;
                    listeners.add(new SttStreamBridge(stt, stt.sampleRate(), SAMPLE_RATE,
                            channelId, sttLanguage, listener, speechGate, metrics));
                } catch (Exception e) {
                    log.warn("STT not started for {}: {}", channelId, e.getMessage());
                }
            }
        }
        return listeners;
    }

    /**
     * The answering-machine detector for this call, or {@code null} when detection is off
     * (§8.6). On detection the dialog is closed as VOICEMAIL and the channel dropped —
     * the rest of a recorded greeting is STT, LLM and TTS spent on nobody.
     */
    private AnsweringMachineDetector buildAmd(String channelId) {
        AmdProperties amd = vadProps.amd();
        if (amd == null || !amd.enabled()) {
            return null;
        }
        return new AnsweringMachineDetector(channelId, SAMPLE_RATE, amd.observeMs(),
                amd.minContinuousSpeechMs(), amd.silenceToleranceMs(),
                () -> handleVoicemail(channelId));
    }

    /** End a call an answering machine answered. Runs on the RTP thread — keep it short. */
    private void handleVoicemail(String channelId) {
        metrics.voicemailDetected();
        // The disposition has to land on the dialog session: that is what teardown reads
        // to write call_attempt.disposition and to schedule the target's retry.
        dialogEngine.notifyVoicemail(channelId);
        callExecutor.execute(() -> withMdc(channelId, () -> hangup(channelId)));
    }

    private CallContext buildTestContext() {
        TestContextProperties t = dialogProps.testContext();
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

    /** Note the cause Asterisk reported for a channel, keeping the first one seen. */
    private void rememberCause(Channel channel, Integer cause) {
        if (channel == null || cause == null) {
            return;
        }
        Integer previous = hangupCauses.putIfAbsent(channel.getId(), cause);
        if (previous == null) {
            log.debug("Hangup cause for {}: {}", channel.getId(), HangupCause.label(cause));
        }
    }

    /**
     * A channel is gone for good. This is the last chance to learn the cause, and the only
     * event an unanswered originate produces at all — so it both persists the cause and
     * settles the outcome of calls that never reached Stasis.
     */
    private void handleChannelDestroyed(ChannelDestroyed event) {
        Channel channel = event.getChannel();
        if (channel == null) {
            return;
        }
        String channelId = channel.getId();
        String name = channel.getName();
        if (name != null && name.startsWith(EXTERNAL_MEDIA_PREFIX)) {
            // Our own media leg. It has no attempt row and no target, so there is nothing
            // to record — only two no-op UPDATEs per call if we let it through.
            hangupCauses.remove(channelId);
            return;
        }
        rememberCause(channel, event.getCause());
        // Removed here and nowhere else: StasisEnd reads the cause synchronously on this
        // same WebSocket thread, and ARI delivers a channel's events in order, so the read
        // always happens before this removal.
        Integer cause = hangupCauses.remove(channelId);

        // Persist the cause even for a call that was already finalized — StasisEnd runs
        // first, so finishAttempt has usually written its row by now, and the column is
        // what a later report explains a NO_ANSWER with.
        callRecordService.recordHangupCause(channelId, HangupCause.label(cause));

        // Settle outbound calls that never answered, instead of leaving them to the
        // 60-second sweeper which can only ever guess NO_ANSWER. Answered calls are left
        // strictly alone: their own teardown owns the outcome, and stealing the registry
        // entry here would record a promise to pay as an unanswered attempt.
        OutboundCall outbound = outboundRegistry.removeIfUnanswered(channelId);
        if (outbound == null) {
            return;
        }
        Disposition disposition = HangupCause.toDisposition(cause);
        if (disposition == null) {
            disposition = Disposition.NO_ANSWER; // rang out, or a cause with no verdict
        }
        dialerState.release();
        campaignService.applyOutcome(outbound.targetId(), disposition);
        metrics.disposition(disposition);
        log.info("Unanswered call {} to {} settled as {} (cause {})",
                channelId, outbound.phone(), disposition, HangupCause.label(cause));
    }

    private void handleStasisEnd(StasisEnd event) {
        String channelId = event.getChannel().getId();
        // Read the dialog outcome before dropping the session, then tear down.
        DialogOutcome outcome = dialogEngine.outcome(channelId);
        dialogEngine.endCall(channelId);
        CallSession session = sessions.remove(channelId);
        if (session == null) {
            return; // externalMedia channel or already torn down
        }
        // Read here, on the event thread, rather than inside the asynchronous teardown:
        // ChannelDestroyed arrives moments later and clears the entry, and teardown may
        // not have been scheduled by then.
        Integer cause = hangupCauses.get(channelId);
        log.info("StasisEnd: channel {} — tearing down media (cause {})",
                channelId, HangupCause.label(cause));
        callExecutor.execute(() -> withMdc(channelId, () -> teardown(session, outcome, cause)));
    }

    private void teardown(CallSession session, DialogOutcome outcome, Integer cause) {
        // What the conversation concluded wins: cause 16 (normal clearing) covers both a
        // promise to pay and an angry hangup, and only the dialog knows which. The cause
        // code is the fallback for calls that produced no outcome of their own (§8.6).
        Disposition disposition = outcome.disposition();
        if (disposition == null) {
            disposition = HangupCause.toDisposition(cause);
            if (disposition != null) {
                log.info("[{}] no dialog outcome; hangup cause {} -> {}",
                        session.channelId(), HangupCause.label(cause), disposition);
            }
        }
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
            // §11.4: an opt-out binds the phone number, not just this campaign's
            // target row, so it is written before the target status is updated.
            if (disposition == Disposition.DO_NOT_CALL) {
                doNotCallRepository.add(outbound.phone(), outcome.doNotCallReason(), "CALL");
            }
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
            throw new ExternalServiceException("asterisk", "ARI is not connected");
        }
        return current;
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(session -> {
            try {
                teardown(session, dialogEngine.outcome(session.channelId()),
                        hangupCauses.get(session.channelId()));
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
