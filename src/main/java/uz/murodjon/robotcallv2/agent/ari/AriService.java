package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.AriVersion;
import ch.loway.oss.ari4java.generated.AriWSHelper;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.ChannelDestroyed;
import ch.loway.oss.ari4java.generated.models.ChannelDtmfReceived;
import ch.loway.oss.ari4java.generated.models.ChannelHangupRequest;
import ch.loway.oss.ari4java.generated.models.StasisEnd;
import ch.loway.oss.ari4java.generated.models.StasisStart;
import ch.loway.oss.ari4java.tools.BaseAriAction;
import io.netty.channel.EventLoopGroup;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import uz.murodjon.robotcallv2.agent.audio.AmbientSoundGenerator;
import uz.murodjon.robotcallv2.agent.audio.AnsweringMachineDetector;
import uz.murodjon.robotcallv2.agent.audio.AudioLevelListener;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.LiveAudioMonitor;
import uz.murodjon.robotcallv2.agent.audio.SpeechGate;
import uz.murodjon.robotcallv2.agent.dialog.CallContext;
import uz.murodjon.robotcallv2.agent.dialog.DialogEngine;
import uz.murodjon.robotcallv2.agent.dialog.DialogOutcome;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.DialogTechnicalSnapshot;
import uz.murodjon.robotcallv2.agent.dialog.LiveDialogSnapshot;
import uz.murodjon.robotcallv2.agent.dialog.RealtimeDialogEngine;
import uz.murodjon.robotcallv2.agent.dialog.TestContextProperties;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.RtpStats;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.agent.rtp.WavRecorder;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.turn.SmartTurnDetector;
import uz.murodjon.robotcallv2.agent.turn.SmartTurnProperties;
import uz.murodjon.robotcallv2.agent.turn.UtteranceBuffer;
import uz.murodjon.robotcallv2.agent.stt.DynamicEndpointingProperties;
import uz.murodjon.robotcallv2.agent.stt.EndpointingProperties;
import uz.murodjon.robotcallv2.agent.stt.SttHints;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProvider;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.agent.stt.TranscriptListener;
import uz.murodjon.robotcallv2.agent.stt.VadGatingProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.agent.vad.AmdProperties;
import uz.murodjon.robotcallv2.agent.vad.SileroVad;
import uz.murodjon.robotcallv2.agent.vad.VadProperties;
import uz.murodjon.robotcallv2.agent.vad.VadStream;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.infrastructure.config.CompanyProperties;
import uz.murodjon.robotcallv2.callrecord.application.dto.CallOriginateResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.LiveCallRow;
import uz.murodjon.robotcallv2.callrecord.application.dto.PlayResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.SayResponse;
import uz.murodjon.robotcallv2.callrecord.application.service.CallFinalizer;
import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.campaign.application.service.CampaignService;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.campaign.domain.entity.CampaignTarget;
import uz.murodjon.robotcallv2.crm.application.service.CrmClient;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.dialer.application.mapper.CallContextMapper;
import uz.murodjon.robotcallv2.dialer.application.service.DialerState;
import uz.murodjon.robotcallv2.dialer.application.service.OutboundCallRegistry;
import uz.murodjon.robotcallv2.memory.application.service.ClientMemoryService;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.donotcall.domain.enums.DoNotCallSource;
import uz.murodjon.robotcallv2.donotcall.application.port.output.DoNotCallRepository;
import uz.murodjon.robotcallv2.inbound.application.service.InboundRouteService;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.live.infrastructure.config.LiveProperties;
import uz.murodjon.robotcallv2.live.application.service.LiveBroadcastService;
import uz.murodjon.robotcallv2.notification.domain.enums.NotificationType;
import uz.murodjon.robotcallv2.notification.application.service.NotificationService;
import uz.murodjon.robotcallv2.operator.infrastructure.config.OperatorProperties;
import uz.murodjon.robotcallv2.scenario.application.service.ScenarioService;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.application.service.FactWebhookClient;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.service.SipTrunkService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns the ARI WebSocket connection and call control (PROJECT.md §8).
 *
 * <p>Stage 3 flow: on {@code StasisStart} the caller is answered, an
 * externalMedia channel is created pointing at a fresh RTP listener, both are
 * joined in a mixing bridge, and incoming audio is recorded to a WAV file. On
 * hangup ({@code StasisEnd}) the media resources are torn down.</p>
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
    /** Tone length in ms. Carrier IVRs stop recognising anything much shorter than this. */
    private static final int DTMF_TONE_MS = 100;
    /** Silence between tones in ms — without a gap a repeated digit reads as one long press. */
    private static final int DTMF_GAP_MS = 100;
    /** An IVR selection is a digit or two; a longer string is a model that has lost the plot. */
    private static final int MAX_DTMF_DIGITS = 12;
    /** {@code PJSIP/<endpoint>-<seq>} -> group 1 is the endpoint (§10.5 "Texnik" tab trunk). */
    private static final Pattern TRUNK_FROM_CHANNEL_NAME = Pattern.compile("PJSIP/(.+)-[0-9a-fA-F]+");
    /** {@link #pendingManualScenarios} sentinel: a manual call with no explicit {@code scenarioId}. */
    private static final long NO_EXPLICIT_SCENARIO = 0L;
    /** First Stasis argument of a browser test call (extensions.conf, extension 700). */
    private static final String WEB_TEST_ARG = "web-test";
    /** Phone recorded on a browser test call's attempt row — there is no number, as with {@code MANUAL}. */
    private static final String WEB_TEST_PHONE = "WEB-TEST";
    /** How long a browser has to dial in after {@link #prepareWebTest} before its session is forgotten. */
    private static final Duration WEB_TEST_SESSION_TTL = Duration.ofMinutes(5);

    /**
     * Inbound packet loss above which a finished call is called out in the log. G.711
     * carries a lost 20ms packet without much complaint; a few percent is where words
     * start going missing and the recognizer begins inventing them.
     */
    private static final double POOR_RTP_LOSS_PERCENT = 5.0;

    private final AsteriskProperties asteriskProperties;
    private final RtpProperties rtpProperties;
    private final RtpPortAllocator portAllocator;
    private final EventLoopGroup rtpEventLoopGroup;
    private final ExecutorService callExecutor;
    private final SttProperties sttProperties;
    private final SttProviderSelector sttProviderSelector;
    private final EngineConfigService engineConfigService;
    private final TtsProperties ttsProperties;
    private final TtsRouter ttsRouter;
    private final DialogProperties dialogProperties;
    private final DialogEngine dialogEngine;
    private final RealtimeDialogEngine realtimeDialogEngine;
    private final DialogRouter dialogRouter;
    private final VadProperties vadProperties;
    private final ObjectProvider<SileroVad> vadProvider;
    private final SmartTurnProperties smartTurnProperties;
    private final ObjectProvider<SmartTurnDetector> turnDetectorProvider;
    private final CallRecordService callRecordService;
    private final CallFinalizer callFinalizer;
    private final OutboundCallRegistry outboundRegistry;
    private final DialerState dialerState;
    private final CampaignService campaignService;
    private final ScenarioService scenarioService;
    private final AiAgentUseCase aiAgentService;
    private final InboundRouteService inboundRouteService;
    private final SipTrunkService sipTrunkService;
    private final CrmClient crmClient;
    private final ClientMemoryService clientMemoryService;
    private final OperatorProperties operatorProperties;
    private final VoiceMetrics metrics;
    private final CallRouteRegistry routeRegistry;
    private final DoNotCallRepository doNotCallRepository;
    private final CompanyProperties companyProperties;
    private final AuditService audit;
    private final LiveBroadcastService broadcast;
    private final LiveProperties liveProperties;
    private final Clock clock;
    private final NotificationService notificationService;
    /** A/B counters for the campaign variant an outbound call was assigned (§ campaign variants). */
    private final CampaignVariantUseCase campaignVariants;
    /** Refreshes an inbound call's facts from the company's own system before the line is answered. */
    private final FactWebhookClient factWebhookClient;

    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();

    /**
     * A manual test call's chosen scenario (ROADMAP A.3, {@code POST /api/calls?
     * scenarioId=}), correlated by channel id between {@link #originateManualCall} and
     * the {@code StasisStart} that follows it — mirrors how {@link OutboundCallRegistry}
     * correlates an outbound call to its context.
     */
    private final Map<String, Long> pendingManualScenarios = new ConcurrentHashMap<>();

    /**
     * An ad-hoc (unsaved) scenario draft to test-drive (backend-uchun-talablar.md §4,
     * {@code POST /api/calls/test}), correlated by channel id the same way {@link
     * #pendingManualScenarios} is. Checked first in {@link #resolveManualScenario} — a
     * channel here never also has a real {@code pendingManualScenarios} entry.
     */
    private final Map<String, ScenarioDefinition> pendingManualDefinitions = new ConcurrentHashMap<>();

    /**
     * The company a manual/test call was placed for, kept until its {@code StasisStart}
     * arrives — that thread has no request behind it, so the tenant cannot be re-derived.
     */
    private final Map<String, Long> pendingManualCompanies = new ConcurrentHashMap<>();

    /**
     * Browser test calls handed out by {@link #prepareWebTest}, keyed by session id until
     * the browser dials in with it (extensions.conf passes it as the second Stasis
     * argument after {@link #WEB_TEST_ARG}); {@link #claimWebTest} then moves the call
     * onto its channel.
     */
    private final Map<String, WebTestCall> pendingWebTests = new ConcurrentHashMap<>();

    /**
     * The campaign profile of a browser test call, keyed by channel. Deliberately kept out
     * of {@link OutboundCallRegistry}: a test holds no dialer slot and has no campaign
     * target to settle, so teardown must find nothing there.
     */
    private final Map<String, OutboundCall> webTestCalls = new ConcurrentHashMap<>();

    /**
     * Q.850 hangup cause per channel, as Asterisk reports it (§8.6). Kept alongside the
     * sessions rather than inside them because the channels that need it most never got a
     * session: an originate that was never answered produces no StasisStart at all, and
     * its cause code is the only thing that distinguishes "line was busy" from "number
     * does not exist".
     */
    private final Map<String, Integer> hangupCauses = new ConcurrentHashMap<>();

    private volatile ARI ari;

    public AriService(AsteriskProperties asteriskProperties,
                      RtpProperties rtpProperties,
                      RtpPortAllocator portAllocator,
                      EventLoopGroup rtpEventLoopGroup,
                      ExecutorService callExecutor,
                      SttProperties sttProperties,
                      SttProviderSelector sttProviderSelector,
                      EngineConfigService engineConfigService,
                      TtsProperties ttsProperties,
                      TtsRouter ttsRouter,
                      DialogProperties dialogProperties,
                      DialogEngine dialogEngine,
                      RealtimeDialogEngine realtimeDialogEngine,
                      DialogRouter dialogRouter,
                      VadProperties vadProperties,
                      ObjectProvider<SileroVad> vadProvider,
                      SmartTurnProperties smartTurnProperties,
                      ObjectProvider<SmartTurnDetector> turnDetectorProvider,
                      CallRecordService callRecordService,
                      CallFinalizer callFinalizer,
                      OutboundCallRegistry outboundRegistry,
                      DialerState dialerState,
                      CampaignService campaignService,
                      ScenarioService scenarioService,
                      AiAgentUseCase aiAgentService,
                      InboundRouteService inboundRouteService,
                      SipTrunkService sipTrunkService,
                      CompanyProperties companyProperties,
                      CrmClient crmClient,
                      ClientMemoryService clientMemoryService,
                      OperatorProperties operatorProperties,
                      VoiceMetrics metrics,
                      CallRouteRegistry routeRegistry,
                      DoNotCallRepository doNotCallRepository,
                      AuditService audit,
                      LiveBroadcastService broadcast,
                      LiveProperties liveProperties,
                      Clock clock,
                      NotificationService notificationService,
                      CampaignVariantUseCase campaignVariants,
                      FactWebhookClient factWebhookClient) {
        this.asteriskProperties = asteriskProperties;
        this.rtpProperties = rtpProperties;
        this.portAllocator = portAllocator;
        this.rtpEventLoopGroup = rtpEventLoopGroup;
        this.callExecutor = callExecutor;
        this.sttProperties = sttProperties;
        this.sttProviderSelector = sttProviderSelector;
        this.engineConfigService = engineConfigService;
        this.ttsProperties = ttsProperties;
        this.ttsRouter = ttsRouter;
        this.dialogProperties = dialogProperties;
        this.dialogEngine = dialogEngine;
        this.realtimeDialogEngine = realtimeDialogEngine;
        this.dialogRouter = dialogRouter;
        this.vadProperties = vadProperties;
        this.vadProvider = vadProvider;
        this.smartTurnProperties = smartTurnProperties;
        this.turnDetectorProvider = turnDetectorProvider;
        this.callRecordService = callRecordService;
        this.callFinalizer = callFinalizer;
        this.outboundRegistry = outboundRegistry;
        this.dialerState = dialerState;
        this.campaignService = campaignService;
        this.scenarioService = scenarioService;
        this.aiAgentService = aiAgentService;
        this.inboundRouteService = inboundRouteService;
        this.sipTrunkService = sipTrunkService;
        this.companyProperties = companyProperties;
        this.crmClient = crmClient;
        this.clientMemoryService = clientMemoryService;
        this.operatorProperties = operatorProperties;
        this.metrics = metrics;
        this.routeRegistry = routeRegistry;
        this.doNotCallRepository = doNotCallRepository;
        this.audit = audit;
        this.broadcast = broadcast;
        this.liveProperties = liveProperties;
        this.clock = clock;
        this.notificationService = notificationService;
        this.campaignVariants = campaignVariants;
        this.factWebhookClient = factWebhookClient;
    }

    /**
     * Say out loud, once, when a turn-taking setting is switched on that cannot reach a
     * call.
     *
     * <p>Every one of them works through {@link SpeechGate}, and the gate is only built
     * when {@code stt.vad-gating.enabled} is on — so with the recognizer endpointing for
     * itself (the default) they are configured, reported as enabled, and inert. That is
     * worth a line at startup: the alternative is a tuning session spent moving numbers
     * nothing reads.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnAboutInertTurnSettings() {
        boolean gated = vadProperties.enabled() && sttProperties.enabled()
                && sttProperties.vadGating() != null && sttProperties.vadGating().enabled();
        if (gated) {
            return;
        }
        if (smartTurnProperties.enabled()) {
            log.warn("voice-agent.turn.enabled is on but no speech gate is installed — Smart Turn "
                    + "(extend and early close) will not run. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
        EndpointingProperties endpointing = sttProperties.endpointing();
        if (endpointing != null && endpointing.enabled()) {
            log.warn("voice-agent.stt.endpointing.enabled is on but no speech gate is installed — "
                    + "the recognizer keeps deciding end-of-utterance. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
        if (endpointing != null && endpointing.dynamic() != null && endpointing.dynamic().enabled()) {
            log.warn("voice-agent.stt.endpointing.dynamic.enabled is on but no speech gate is installed — "
                    + "the wait will not adapt. It needs voice-agent.stt.vad-gating.enabled=true.");
        }
    }

    /**
     * Generates the ambient beds before any call needs one. Without this the class
     * initializes on the first frame that mixes a bed, which runs on the RTP pacer's
     * Netty event loop (CLAUDE.md §8.3) and stalls the first call's audio for ~0.6s.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpAmbientSounds() {
        long startNanos = System.nanoTime();
        AmbientSoundGenerator.warmUp();
        log.info("Ambient soundscapes generated in {}ms", (System.nanoTime() - startNanos) / 1_000_000);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void connect() {
        if (!asteriskProperties.enabled()) {
            log.info("Asterisk ARI disabled (voice-agent.asterisk.enabled=false); not connecting");
            return;
        }
        try {
            BaseAriAction.setObjectMapperLessStrict();
            ari = ARI.build(asteriskProperties.ariUrl(), asteriskProperties.appName(), asteriskProperties.ariUser(), asteriskProperties.ariPassword(),
                    AriVersion.IM_FEELING_LUCKY);
            ari.events().eventWebsocket(asteriskProperties.appName()).execute(new AriWSHelper() {
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
                    rememberCause(event.getChannel(), event.getCause());
                }

                @Override
                public void onChannelDestroyed(ChannelDestroyed event) {
                    handleChannelDestroyed(event);
                }

                @Override
                public void onChannelDtmfReceived(ChannelDtmfReceived event) {
                    handleDtmf(event);
                }
            });
            log.info("Connected to Asterisk ARI at {} as app '{}'", asteriskProperties.ariUrl(), asteriskProperties.appName());
        } catch (Exception e) {
            log.error("Failed to connect to Asterisk ARI at {}: {}", asteriskProperties.ariUrl(), e.getMessage());
        }
    }

    public String originate(String rawNumber, long companyId) {
        return originate(rawNumber, companyId, (Long) null);
    }

    public String originate(String rawNumber, long companyId, OutboundCall outboundCall) {
        Long trunkId = outboundCall != null ? outboundCall.sipTrunkId() : null;
        return originate(rawNumber, companyId, trunkId, outboundCall);
    }

    public String originate(String rawNumber, long companyId, Long sipTrunkId) {
        return originate(rawNumber, companyId, sipTrunkId, null);
    }

    private String originate(String rawNumber, long companyId, Long sipTrunkId, OutboundCall outboundCall) {
        String number = PhoneNumbers.require(rawNumber);
        String callId = "call-" + System.currentTimeMillis();
        SipTrunkRow trunk = isLocalNumber(number) ? null : resolveTrunkForCall(companyId, sipTrunkId);
        String endpoint = endpointFor(number, trunk);
        String callerId = callerIdFor(trunk);
        String dialNumber = !isLocalNumber(number) && number.startsWith("+")
                ? number.substring(1)
                : number;
        long targetId = outboundCall != null ? outboundCall.targetId() : callRecordService.manualTargetId();
        // No OutboundCall means a manual/test originate, and StasisStart drives exactly
        // those with dialogProperties.language(). Reading it here too keeps the row and the
        // conversation on one language — the row is written now and never rewritten, so a
        // literal here left the call held in uz-UZ but reported as "uz".
        String language = outboundCall != null ? outboundCall.language() : dialogProperties.language();
        try {
            // Inside the try on purpose: an ARI connection that is down is one more way
            // for the call never to happen, and it has to leave a row like the rest.
            var request = requireConnection().channels()
                    .originate("PJSIP/" + dialNumber + "@" + endpoint)
                    .setApp(asteriskProperties.appName())
                    .setAppArgs(callId)
                    .setTimeout(asteriskProperties.answerTimeoutSec());
            if (callerId != null && !callerId.isBlank()) {
                request.setCallerId(callerId);
            }
            Channel channel = request.execute();
            String channelId = channel.getId();
            callRecordService.startAttempt(companyId, targetId, channelId, number, language, null);
            if (outboundCall != null) {
                outboundRegistry.register(channelId, outboundCall);
            }
            log.info("Originated call {} to {} (dial={}) via {} (trunkId={}) -> channel {}",
                    callId, number, dialNumber, endpoint, sipTrunkId, channelId);
            return channelId;
        } catch (Exception e) {
            // Asterisk never took the call, so no channel and no ChannelDestroyed will
            // ever arrive to close a row — write the closed row here instead, or the
            // attempt the dialer already counted leaves no trace of why it failed.
            callRecordService.recordUnplacedAttempt(companyId, targetId, number, language,
                    Disposition.FAILED, "originate via " + endpoint + " failed: " + e.getMessage());
            throw new ExternalServiceException(ErrorCode.ARI_ORIGINATE_FAILED, "asterisk", e,
                    number, endpoint, e.getMessage());
        }
    }

    public CallOriginateResponse originateManualCall(long companyId, String number, Long scenarioId) {
        return originateManualCall(companyId, number, scenarioId, null);
    }

    public CallOriginateResponse originateManualCall(long companyId, String number, Long scenarioId, Long sipTrunkId) {
        if (scenarioId != null) {
            scenarioService.requireScenario(companyId, scenarioId);
        }
        String channelId = originate(number, companyId, sipTrunkId);
        pendingManualScenarios.put(channelId, scenarioId != null ? scenarioId : NO_EXPLICIT_SCENARIO);
        pendingManualCompanies.put(channelId, companyId);
        audit.record(companyId, "CALL_ORIGINATE_MANUAL", "call", channelId, number);
        return new CallOriginateResponse(number, channelId);
    }

    public CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition) {
        return originateTestCall(companyId, number, definition, null);
    }

    public CallOriginateResponse originateTestCall(long companyId, String number, ScenarioDefinition definition,
                                                   Long sipTrunkId) {
        String channelId = originate(number, companyId, sipTrunkId);
        pendingManualDefinitions.put(channelId, definition);
        pendingManualCompanies.put(channelId, companyId);
        audit.record(companyId, "CALL_ORIGINATE_TEST", "call", channelId, number);
        return new CallOriginateResponse(number, channelId);
    }

    /**
     * Registers a browser test call ({@code POST /api/calls/web-test}) and returns the
     * session id the browser must dial in with. With a {@code campaignId} the call runs
     * under that campaign's full profile (scenario, voices, persona, ambient sound, ...)
     * and, given a {@code targetId}, that target's facts; otherwise it is a manual test
     * call of the draft, the scenario, or the default test scenario.
     */
    public String prepareWebTest(long companyId, Long campaignId, Long targetId, Long scenarioId,
                                 ScenarioDefinition definition) {
        Instant cutoff = Instant.now().minus(WEB_TEST_SESSION_TTL);
        pendingWebTests.values().removeIf(test -> test.createdAt().isBefore(cutoff));
        OutboundCall outbound = campaignId != null ? webTestProfile(companyId, campaignId, targetId) : null;
        String sessionId = UUID.randomUUID().toString();
        pendingWebTests.put(sessionId, new WebTestCall(outbound, definition, scenarioId, Instant.now()));
        audit.record(companyId, "CALL_WEB_TEST", "call", sessionId,
                campaignId != null ? "campaign " + campaignId + (targetId != null ? " target " + targetId : "") : null);
        return sessionId;
    }

    /**
     * The campaign's call profile for a browser test — what {@code CallTaskConsumer} builds
     * for a real target, minus the target itself: the attempt is booked on the manual
     * target and the phone is a placeholder, so no campaign statistic moves.
     */
    private OutboundCall webTestProfile(long companyId, long campaignId, Long targetId) {
        Campaign campaign = campaignService.requireCampaign(companyId, campaignId);
        AiAgent agent = aiAgentService.requireAgent(campaign.companyId(), campaign.aiAgentId());
        Scenario scenario = scenarioService.requireScenario(campaign.companyId(), agent.scenarioId());
        CampaignTarget target = targetId != null
                ? campaignService.requireTarget(companyId, campaignId, targetId) : null;
        String language = target != null && target.language() != null ? target.language() : agent.language();
        CallContext context;
        if (target != null) {
            CrmClientSnapshot crm = crmClient.fetchClient(campaign.companyId(), target.clientId());
            ClientMemory memory = clientMemoryService.findByCompanyIdAndPhone(campaign.companyId(), target.phone());
            context = CallContextMapper.merge(
                    CallContextMapper.fromJson(target.contextData(), null, scenario.definition().factSchema()), crm)
                    .withMemory(memory);
        } else {
            context = buildTestContext(scenario.definition());
        }
        return new OutboundCall(campaign.id(), callRecordService.manualTargetId(), null, WEB_TEST_PHONE,
                language, agent.voiceFor(language), context, agent, campaign.companyId(),
                null, null, null);
    }

    /**
     * Moves the web test session the browser dialled in with onto its channel, so
     * {@link #setupMedia} finds it the way it finds any other test call. False when the
     * session is unknown or already expired — the channel is then hung up.
     */
    private boolean claimWebTest(String channelId, String sessionId) {
        WebTestCall test = sessionId != null ? pendingWebTests.remove(sessionId) : null;
        if (test == null) {
            log.warn("Web test call {} carries unknown or expired session {} — hanging up", channelId, sessionId);
            return false;
        }
        if (test.outbound() != null) {
            webTestCalls.put(channelId, test.outbound());
        } else if (test.definition() != null) {
            pendingManualDefinitions.put(channelId, test.definition());
        } else {
            pendingManualScenarios.put(channelId, test.scenarioId() != null ? test.scenarioId() : NO_EXPLICIT_SCENARIO);
        }
        log.info("Web test session {} claimed by channel {}", sessionId, channelId);
        return true;
    }

    /** The campaign profile behind a channel: a dialer call, or a browser test of a campaign. */
    private OutboundCall outboundFor(String channelId) {
        OutboundCall outbound = outboundRegistry.peek(channelId);
        return outbound != null ? outbound : webTestCalls.get(channelId);
    }

    private SipTrunkRow resolveTrunkForCall(long companyId, Long sipTrunkId) {
        if (sipTrunkId != null) {
            try {
                return sipTrunkService.requireTrunk(companyId, sipTrunkId);
            } catch (Exception e) {
                log.warn("Requested sipTrunkId {} could not be loaded, falling back to default: {}", sipTrunkId, e.getMessage());
            }
        }
        return sipTrunkService.findDefaultForCall(companyId);
    }

    private boolean isLocalNumber(String number) {
        String local = asteriskProperties.localEndpoint();
        String pattern = asteriskProperties.localNumberPattern();
        return local != null && !local.isBlank()
                && pattern != null && !pattern.isBlank()
                && number != null && number.matches(pattern);
    }

    private String endpointFor(String number, SipTrunkRow trunk) {
        if (isLocalNumber(number)) {
            return asteriskProperties.localEndpoint();
        }
        return trunk != null ? trunk.pjsipEndpoint() : asteriskProperties.trunkEndpoint();
    }

    private String callerIdFor(SipTrunkRow trunk) {
        if (trunk != null && trunk.callerId() != null && !trunk.callerId().isBlank()) {
            return trunk.callerId();
        }
        return asteriskProperties.callerId();
    }

    private static String trunkOf(String channelName) {
        if (channelName == null) {
            return null;
        }
        Matcher m = TRUNK_FROM_CHANNEL_NAME.matcher(channelName);
        return m.matches() ? m.group(1) : null;
    }

    public void play(String channelId, Path file) {
        CallSession session = sessions.get(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        try {
            WavAudio audio = WavReader.read(file);
            if (audio.sampleRate() != SAMPLE_RATE) {
                log.warn("Playback file {} is {} Hz, expected {} Hz — audio may sound wrong",
                        file, audio.sampleRate(), SAMPLE_RATE);
            }
            session.endpoint().playPcm(audio.samples());
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.ARI_PLAYBACK_FAILED, "asterisk", e, file, e.getMessage());
        }
    }

    public PlayResponse playRecording(String channelId, String file) {
        Path resolved = resolveInRecordingDir(file);
        play(channelId, resolved);
        return new PlayResponse(channelId, resolved.toString(), "playing");
    }

    private Path resolveInRecordingDir(String file) {
        if (file == null || file.isBlank()) {
            throw new ValidationException(ErrorCode.PLAYBACK_FILE_BLANK);
        }
        Path base = Path.of(rtpProperties.recordingDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(file).normalize();
        if (!resolved.startsWith(base)) {
            throw new ValidationException(ErrorCode.PLAYBACK_FILE_OUTSIDE_BASE, base);
        }
        try {
            Path real = resolved.toRealPath();
            if (!real.startsWith(base.toRealPath())) {
                throw new ValidationException(ErrorCode.PLAYBACK_FILE_OUTSIDE_BASE, base);
            }
            return real;
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.PLAYBACK_FILE_NOT_FOUND, file);
        }
    }

    public void speak(String channelId, String text, String language) {
        speak(channelId, text, language, null);
    }

    public void speak(String channelId, String text, String language, String ttsVoice) {
        CallSession session = sessions.get(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        if (!ttsProperties.enabled()) {
            throw new ConflictException(ErrorCode.TTS_DISABLED);
        }
        String lang = (language == null || language.isBlank()) ? ttsProperties.defaultLanguage() : language;
        short[] pcm = ttsRouter.synthesize(text, lang, ttsVoice);
        log.info("TTS speak [{}] lang={} voice={} chars={} -> {} samples",
                channelId, lang, ttsVoice != null ? ttsVoice : "default", text.length(), pcm.length);
        session.endpoint().playPcm(pcm);
    }

    public SayResponse say(String channelId, String text, String language, String voice) {
        if (text.isBlank()) {
            throw new ValidationException(ErrorCode.SAY_TEXT_BLANK);
        }
        if (text.length() > MAX_SAY_CHARS) {
            throw new ValidationException(ErrorCode.SAY_TEXT_TOO_LONG, MAX_SAY_CHARS);
        }
        speak(channelId, text, language, voice);
        return new SayResponse(channelId, text, language, "speaking");
    }

    public StreamingResponseBody listen(String channelId) {
        CallSession session = sessions.get(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        LiveAudioMonitor monitor = session.audioMonitor();
        return output -> {
            BlockingQueue<byte[]> queue = monitor.subscribe();
            try {
                output.write(WavHeader.bytes(SAMPLE_RATE, 1, Integer.MAX_VALUE - WavHeader.SIZE));
                output.flush();
                while (true) {
                    byte[] chunk;
                    try {
                        chunk = queue.poll(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (chunk == null) {
                        continue;
                    }
                    if (chunk == LiveAudioMonitor.EOF) {
                        break;
                    }
                    output.write(chunk);
                    output.flush();
                }
            } finally {
                monitor.unsubscribe(queue);
            }
        };
    }

    public List<LiveCallRow> liveCalls() {
        return dialogRouter.liveDialogs().stream()
                .map(this::toLiveCallRow)
                .toList();
    }

    private LiveCallRow toLiveCallRow(LiveDialogSnapshot s) {
        OutboundCall outbound = outboundFor(s.channelId());
        Long campaignId = outbound != null ? outbound.campaignId() : null;
        String phone = outbound != null ? outbound.phone() : null;
        String campaignName = null;
        if (campaignId != null) {
            Campaign campaign = campaignService.getCampaign(outbound.companyId(), campaignId);
            campaignName = campaign != null ? campaign.name() : null;
        }
        CallSession session = sessions.get(s.channelId());
        String trunk = session != null ? session.trunk() : null;
        return new LiveCallRow(s.channelId(), phone, s.clientName(), campaignId, campaignName,
                s.language(), s.startedAt(), s.dialogState(), trunk);
    }

    private void handleStasisStart(StasisStart event) {
        Channel channel = event.getChannel();
        String channelId = channel.getId();
        String name = channel.getName();
        if (name != null && name.startsWith(EXTERNAL_MEDIA_PREFIX)) {
            log.debug("Ignoring externalMedia StasisStart for {} ({})", channelId, name);
            return;
        }
        List<String> args = event.getArgs();
        if (args != null && !args.isEmpty() && "operator".equals(args.get(0))) {
            log.info("StasisStart (operator): channel {} args {}", channelId, args);
            callExecutor.execute(() -> handleOperatorJoin(channel, args));
            return;
        }
        if (args != null && !args.isEmpty() && WEB_TEST_ARG.equals(args.get(0))
                && !claimWebTest(channelId, args.size() > 1 ? args.get(1) : null)) {
            hangup(channelId);
            return;
        }
        log.info("StasisStart: channel {} ({}) args {}", channelId, name, args);
        callExecutor.execute(() -> withMdc(channelId, () -> setupMedia(channelId, name, channel)));
    }

    private static void withMdc(String channelId, Runnable body) {
        MDC.put("channelId", channelId);
        try {
            body.run();
        } finally {
            MDC.remove("channelId");
        }
    }

    /**
     * A keypad digit on a call whose campaign asked for them
     * ({@code campaign.dtmf_input_enabled}). Asterisk decodes RFC2833 itself, so the digit
     * arrives as a character on the event stream and never touches the RTP path.
     *
     * <p>Handled exactly like a spoken answer: it silences a bot that was mid-sentence and
     * becomes the caller's turn. Only {@code 0-9} — {@code *}, {@code #} and the A-D tones
     * mean nothing in any scenario here, and handing one to the model as an utterance
     * would only buy a puzzled reply.
     */
    private void handleDtmf(ChannelDtmfReceived event) {
        Channel channel = event.getChannel();
        String digit = event.getDigit();
        if (channel == null || digit == null || digit.length() != 1
                || digit.charAt(0) < '0' || digit.charAt(0) > '9') {
            return;
        }
        String channelId = channel.getId();
        CallSession session = sessions.get(channelId);
        if (session == null || !session.dtmfInputEnabled()) {
            return;
        }
        if (!dialogEngine.owns(channelId)) {
            // A speech-to-speech engine takes audio and nothing else; there is no turn to
            // hand a digit to. Said out loud rather than dropped, because a campaign that
            // switched on keypad input is expecting it to do something.
            log.warn("[{}] DTMF '{}' ignored — keypad input needs the cascade pipeline", channelId, digit);
            return;
        }
        callExecutor.execute(() -> withMdc(channelId, () -> {
            log.info("[{}] DTMF: {}", channelId, digit);
            int offsetMs = (int) (Instant.now().toEpochMilli() - session.startedAt().toEpochMilli());
            callRecordService.addTranscript(session.callAttemptId(), "CLIENT", digit, null, offsetMs, 1f);
            dialogEngine.notifyBargeIn(channelId);
            dialogEngine.onClientFinal(channelId, digit);
        }));
    }

    public void transferToOperator(String channelId) {
        CallSession session = sessions.get(channelId);
        if (!operatorProperties.enabled() || operatorProperties.endpoint() == null || operatorProperties.endpoint().isBlank()
                || session == null) {
            log.info("Operator transfer unavailable for {}; hanging up", channelId);
            hangup(channelId);
            return;
        }
        try {
            requireConnection().channels()
                    .originate(operatorProperties.endpoint())
                    .setApp(asteriskProperties.appName())
                    .setAppArgs("operator," + session.bridgeId() + "," + channelId)
                    .setTimeout(operatorProperties.answerTimeoutSec())
                    .execute();
            log.info("Transferring {} to operator {} (bridge {})",
                    channelId, operatorProperties.endpoint(), session.bridgeId());
            notificationService.notify(callRecordService.companyIdOf(session.callAttemptId()),
                    NotificationType.OPERATOR_REQUEST,
                    "Operatorga so'rov", "Qo'ng'iroq " + channelId + " operatorga uzatildi", null);
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

    private void setupMedia(String channelId, String channelName, Channel channel) {
        OutboundCall outbound = outboundFor(channelId);
        boolean manual = pendingManualScenarios.containsKey(channelId) || pendingManualDefinitions.containsKey(channelId);
        InboundRoute inboundRoute = null;
        if (outbound == null && !manual) {
            String did = extractDid(channel);
            inboundRoute = inboundRouteService.resolveByDid(did);
            // A route with no agent answers with a queue, an IVR or a user — nothing this
            // service speaks. Treated like no route at all rather than dialled into a
            // scenario that does not exist.
            if (inboundRoute != null && inboundRoute.aiAgentId() == null) {
                log.info("Inbound call {} to {} routes to {} — not an AI agent", channelId, did,
                        inboundRoute.routeType());
                hangup(channelId);
                return;
            }
            if (inboundRoute == null || !withinBusinessHours(inboundRoute)) {
                if (inboundRoute != null && inboundRoute.fallbackMessage() != null
                        && !inboundRoute.fallbackMessage().isBlank()) {
                    log.info("Inbound call {} to {} outside business hours; playing fallback message",
                            channelId, did);
                    playFallbackAndHangup(channelId, inboundRoute);
                } else {
                    log.info("Inbound call {} to {} not answered by AI: {}", channelId, did,
                            inboundRoute == null ? "no matching route" : "outside business hours");
                    hangup(channelId);
                }
                return;
            }
        }

        ARI current = requireConnection();
        int port = portAllocator.allocate();
        RtpEndpoint endpoint = null;
        long attemptId = 0;
        try {
            // Ahead of the media and ahead of the answer, because this is the only window an
            // inbound call has for it. The caller is still hearing ringback here, so a slow
            // endpoint costs a moment of ringing; asked after the answer it would be a
            // silence on an open line — the very thing the ordering below exists to remove.
            // Outbound calls are served earlier still, in CallTaskConsumer, before dialling.
            Scenario inboundScenario = null;
            AiAgent inboundAgent = null;
            String inboundFacts = null;
            if (inboundRoute != null) {
                inboundAgent = aiAgentService.requireAgent(inboundRoute.companyId(), inboundRoute.aiAgentId());
                inboundScenario = scenarioService.requireScenario(inboundRoute.companyId(), inboundAgent.scenarioId());
                inboundFacts = factWebhookClient.fetchFacts(inboundScenario.definition().factWebhook(),
                        FactWebhookRequest.inbound(extractCallerNumber(channel), extractDid(channel)));
            }

            Files.createDirectories(Path.of(rtpProperties.recordingDir()));
            Path wav = Path.of(rtpProperties.recordingDir(), channelId.replace('/', '_') + ".wav");
            WavRecorder recorder = new WavRecorder(wav, SAMPLE_RATE, rtpProperties.recordingMode());

            // Media before the line opens. Answering first and then looking the caller up
            // left the call connected with no RTP path for as long as that lookup took —
            // a CRM HTTP round trip on an inbound call — and everything said into that gap
            // was lost. Here the socket is bound, externalMedia is up and Asterisk has been
            // told where to send audio before anyone can speak; the listener chain that
            // needs the database is swapped in afterwards (RtpEndpoint#replaceListeners).
            LiveAudioMonitor audioMonitor = new LiveAudioMonitor(rtpEventLoopGroup);
            endpoint = new RtpEndpoint(port, rtpProperties.codec(), recorder, List.<AudioListener>of(audioMonitor),
                    audioMonitor::onBotAudio);
            if (outbound != null && outbound.agent().ambientSound() != null) {
                endpoint.setAmbientSound(outbound.agent().ambientSound());
            }
            endpoint.bind(rtpEventLoopGroup);

            Channel extMedia = current.channels()
                    .externalMedia(asteriskProperties.appName(), rtpProperties.localIp() + ":" + port, rtpProperties.codec().asteriskFormat())
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute();
            pointPlaybackAt(current, extMedia.getId(), endpoint);

            Bridge bridge = current.bridges().create().setType("mixing").execute();
            current.bridges().addChannel(bridge.getId(), extMedia.getId()).execute();

            // Only now. An unanswered channel added to a bridge is answered implicitly, so
            // the explicit answer has to come first for the ordering above to mean anything.
            answer(channelId);
            current.bridges().addChannel(bridge.getId(), channelId).execute();

            long targetId;
            String language;
            String ttsVoice;
            CallContext context;
            Scenario scenarioRow;
            String phone;
            long companyId;
            // Null only on a manual test call, which runs a scenario with no agent behind
            // it and therefore falls back to the platform defaults everywhere below.
            AiAgent agent;

            if (outbound != null) {
                outboundRegistry.markAnswered(channelId);
                agent = outbound.agent();
                targetId = outbound.targetId();
                language = outbound.language() != null ? outbound.language() : dialogProperties.language();
                ttsVoice = outbound.ttsVoice();
                context = outbound.context();
                scenarioRow = scenarioService.requireScenario(outbound.companyId(), agent.scenarioId());
                phone = outbound.phone();
                companyId = outbound.companyId();
            } else if (manual) {
                agent = null;
                targetId = callRecordService.manualTargetId();
                language = dialogProperties.language();
                ttsVoice = dialogProperties.ttsVoice();
                Long manualCompanyId = pendingManualCompanies.remove(channelId);
                companyId = manualCompanyId != null ? manualCompanyId : companyProperties.defaultId();
                scenarioRow = resolveManualScenario(companyId, channelId);
                context = buildTestContext(scenarioRow.definition());
                phone = "MANUAL";
            } else {
                // The same object an outbound call reads its voice and persona from. Before
                // the agent existed an inbound call had no campaign to read them from, so it
                // always answered as AI_ASSISTANT in the company's default voice.
                agent = inboundAgent;
                targetId = callRecordService.inboundTargetId();
                scenarioRow = inboundScenario;
                String callerNumber = extractCallerNumber(channel);
                phone = callerNumber != null ? callerNumber : "INBOUND";
                companyId = inboundRoute.companyId();
                CrmClientSnapshot crm = callerNumber != null
                        ? crmClient.findByPhone(companyId, callerNumber) : null;
                ClientMemory memory = callerNumber != null
                        ? clientMemoryService.findByCompanyIdAndPhone(companyId, callerNumber) : null;
                // Read after the lookups, not before — a caller the company already knows to
                // be Russian-speaking used to be answered in the agent's language on every
                // inbound call, while the same person was called in Russian outbound.
                language = CallLanguage.resolve(
                        crm != null ? crm.preferredLanguage() : null,
                        memory != null ? memory.preferredLanguage() : null,
                        agent.language());
                ttsVoice = agent.voiceFor(language);
                // The webhook's answer goes on last, over the CRM: it was asked most recently.
                context = CallContextMapper.overlayJson(
                        CallContextMapper.merge(new CallContext(Map.of(), null), crm).withMemory(memory),
                        inboundFacts, scenarioRow.definition().factSchema());
            }

            Instant startedAt = Instant.now();
            attemptId = callRecordService.findAttemptIdByChannel(channelId);
            if (attemptId == 0) {
                attemptId = callRecordService.startAttempt(companyId, targetId, channelId, phone, language,
                        inboundRoute != null ? inboundRoute.id() : null);
            }
            callRecordService.markAnswered(attemptId);
            // The A/B denominator. Dispatch counted the attempt; this counts the ones that
            // reached a human, which is the only population a conversion rate means anything
            // over — a variant is not worse because its half of the list picked up less.
            if (outbound != null && outbound.variantId() != null) {
                campaignVariants.recordAnswer(outbound.variantId());
            }

            EffectiveEngineConfig engineConfig =
                    engineConfigService.findEffectiveByCompanyId(callRecordService.companyIdOf(attemptId));
            boolean realtime = engineConfig.mode() == PipelineMode.REALTIME && realtimeDialogEngine.available();
            if (engineConfig.mode() == PipelineMode.REALTIME && !realtime) {
                log.warn("[{}] company is set to REALTIME but no engine is available — running cascade", channelId);
            }
            RealtimeAudioBridge realtimeBridge = realtime ? new RealtimeAudioBridge() : null;

            List<AudioListener> audioListeners = buildAudioListeners(channelId, attemptId, startedAt, language,
                    engineConfig, realtimeBridge, context);
            audioListeners.add(audioMonitor);
            endpoint.replaceListeners(audioListeners);

            sessions.put(channelId, new CallSession(channelId, extMedia.getId(), bridge.getId(),
                    port, endpoint, attemptId, startedAt, wav.toString(), channelName, trunkOf(channelName),
                    scenarioRow.id(), audioMonitor, agent != null && agent.dtmfInputEnabled()));
            log.info("Media ready for {}: rtpPort={}, extMedia={}, bridge={}, wav={}, attempt={}",
                    channelId, port, extMedia.getId(), bridge.getId(), wav, attemptId);

            metrics.callStarted();
            metrics.activeInc();
            routeRegistry.register(channelId);

            String testFile = rtpProperties.testPlaybackFile();
            if (testFile != null && !testFile.isBlank()) {
                play(channelId, Path.of(testFile));
            }

            boolean startDialog = !manual || dialogProperties.autoStart();
            // Keypad tones only on calls we placed. Inbound is a person who dialled us —
            // there is no menu on their end to navigate, and a bot pressing buttons into a
            // live caller's ear is only ever a bug.
            Consumer<String> dtmfSender = outbound != null ? digits -> sendDtmf(channelId, digits) : null;
            // An A/B variant may replace the scenario's role prompt for this call only; the
            // scenario row itself is untouched, so the other variant's calls are unaffected.
            ScenarioDefinition definition = outbound != null
                    ? scenarioRow.definition().withRolePrompt(outbound.promptOverride())
                    : scenarioRow.definition();
            if (dialogProperties.enabled() && startDialog) {
                if (realtime) {
                    boolean started = realtimeDialogEngine.startCall(channelId, endpoint, context,
                            definition, language, ttsVoice, agent,
                            () -> hangup(channelId), () -> transferToOperator(channelId), attemptId,
                            realtimeBridge, null, dtmfSender);
                    if (!started) {
                        log.error("[{}] realtime dialog did not start — hanging up", channelId);
                        hangup(channelId);
                    }
                } else {
                    dialogEngine.startCall(channelId, endpoint, context, definition, language, ttsVoice,
                            agent, () -> hangup(channelId), () -> transferToOperator(channelId),
                            attemptId, dtmfSender);
                }
            }
        } catch (Exception e) {
            log.error("Failed to set up media for {}: {}", channelId, e.getMessage(), e);
            callRecordService.recordError(attemptId, e.toString());
            // Whoever holds the session owns the cleanup. Once it is registered, the
            // hangup below produces a StasisEnd and teardown does all of this; doing it
            // here as well handed the same RTP port back twice. Between the two releases
            // that port can be allocated to another call, and the second release puts a
            // port that call is bound to back in the free set — the next call to draw it
            // then fails to bind. So: clean up only what teardown will never see.
            if (!sessions.containsKey(channelId)) {
                if (endpoint != null) {
                    endpoint.close();
                }
                portAllocator.release(port);
            }
            hangup(channelId);
        }
    }

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
                                                    String language, EffectiveEngineConfig engineConfig,
                                                    RealtimeAudioBridge realtimeBridge, CallContext context) {
        List<AudioListener> listeners = new ArrayList<>();
        boolean realtime = realtimeBridge != null;

        if (liveProperties.audioLevelEnabled()) {
            listeners.add(new AudioLevelListener(channelId, broadcast));
        }

        SpeechGate speechGate = null;
        if (vadProperties.enabled()) {
            SileroVad vad = vadProvider.getIfAvailable();
            if (vad != null && vad.available()) {
                VadGatingProperties gating = sttProperties.vadGating();
                if (!realtime && gating != null && gating.enabled() && sttProperties.enabled()) {
                    EndpointingProperties endpointing = sttProperties.endpointing();
                    boolean adaptive = endpointing != null && endpointing.enabled();
                    DynamicEndpointingProperties dynamic = adaptive ? endpointing.dynamic() : null;
                    boolean learning = dynamic != null && dynamic.enabled();
                    speechGate = new SpeechGate(SAMPLE_RATE, gating.preRollMs(), gating.postRollMs(),
                            gating.minSpeechMs(),
                            adaptive ? endpointing.shortUtteranceMs() : 0,
                            adaptive ? endpointing.shortSilenceMs() : 0,
                            learning ? dynamic.minPostRollMs() : 0,
                            learning ? dynamic.emaAlpha() : 0d,
                            learning ? dynamic.reopenGraceMs() : 0);
                    speechGate.setTranscriptCues(adaptive ? endpointing.completeSilenceMs() : 0);
                    installTurnDetector(listeners, speechGate, language);
                }
                // Who knows the caller stopped talking. With a gate installed it is the
                // gate's close, and SttStreamBridge reports that. Without one — the
                // default, where the recognizer endpoints for itself — nothing did, and
                // the silence in front of the final sat outside every latency number the
                // app reports (VOICE-QUALITY-PLAN 0.1). The VAD is already scoring every
                // window for barge-in, so it can say so at no extra cost.
                IntConsumer onSpeechEnd = realtime || speechGate != null ? null
                        : waitMs -> dialogEngine.notifyUtteranceEnd(channelId, waitMs);
                listeners.add(new VadStream(vad, vadProperties, channelId,
                        realtime ? () -> false : () -> dialogEngine.notifyBargeIn(channelId), speechGate,
                        buildAmd(channelId), onSpeechEnd));
            } else if (!realtime && sttProperties.vadGating() != null && sttProperties.vadGating().enabled()) {
                log.debug("[{}] STT gating requested but VAD is unavailable — streaming all audio", channelId);
            }
        }

        if (realtime) {
            listeners.add(realtimeBridge);
            return listeners;
        }

        if (sttProperties.enabled()) {
            SttProvider stt = sttProviderSelector.findForCall(engineConfig.sttProvider());
            if (stt != null) {
                boolean dialog = dialogProperties.enabled() && dialogEngine.available();
                TranscriptListener listener = (text, isFinal, confidence) -> {
                    if (isFinal) {
                        log.info("[{}] FINAL (conf={}): {}", channelId, confidence, text);
                        int offsetMs = (int) (Instant.now().toEpochMilli() - startedAt.toEpochMilli());
                        callRecordService.addTranscript(callAttemptId, "CLIENT", text, null, offsetMs, confidence);
                        if (dialog) {
                            dialogEngine.onClientFinal(channelId, text, confidence);
                        }
                    } else {
                        log.debug("[{}] interim: {}", channelId, text);
                        if (dialog) {
                            dialogEngine.onClientInterim(channelId, text);
                        }
                    }
                };
                try {
                    String sttLanguage = (language == null || language.isBlank())
                            ? sttProperties.defaultLanguage() : language;
                    List<String> detectLangs = sttProperties.detectLanguages() != null ? sttProperties.detectLanguages() : List.of();
                    listeners.add(new SttStreamBridge(stt, stt.sampleRate(), SAMPLE_RATE,
                            channelId, sttLanguage, detectLangs, listener, speechGate, metrics,
                            sttProperties.endpointing(), sttProperties.responseTimeoutMs(),
                            dialog ? eouWaitMs -> dialogEngine.notifyUtteranceEnd(channelId, eouWaitMs) : null,
                            SttHints.of(context), sttProviderSelector));
                } catch (Exception e) {
                    log.warn("STT not started for {}: {}", channelId, e.getMessage());
                }
            }
        }
        return listeners;
    }

    private void installTurnDetector(List<AudioListener> listeners, SpeechGate gate, String language) {
        if (!smartTurnProperties.enabled()) {
            return;
        }
        SmartTurnDetector detector = turnDetectorProvider.getIfAvailable();
        if (detector == null || !detector.available()) {
            return;
        }
        String callLanguage = (language == null || language.isBlank())
                ? sttProperties.defaultLanguage() : language;
        if (!smartTurnProperties.supports(callLanguage)) {
            log.debug("Smart Turn skipped for {} — not a language the model was trained for", callLanguage);
            return;
        }
        UtteranceBuffer buffer = new UtteranceBuffer(detector.samples());
        listeners.add(buffer);
        gate.setTurnDetector(() -> {
            boolean complete = detector.isComplete(buffer.recent(), buffer.length());
            metrics.turnScored(complete);
            return complete;
        }, smartTurnProperties.maxExtendMs());
        if (smartTurnProperties.earlyWaitMs() > 0) {
            gate.setEarlyClose(() -> {
                boolean confident = detector.isConfidentlyComplete(buffer.recent(), buffer.length());
                if (confident) {
                    metrics.turnClosedEarly();
                }
                return confident;
            }, smartTurnProperties.earlyWaitMs());
        }
    }

    private AnsweringMachineDetector buildAmd(String channelId) {
        AmdProperties amd = vadProperties.amd();
        if (amd == null || !amd.enabled()) {
            return null;
        }
        return new AnsweringMachineDetector(channelId, SAMPLE_RATE, amd.observeMs(),
                amd.minContinuousSpeechMs(), amd.silenceToleranceMs(),
                () -> handleVoicemail(channelId));
    }

    private void handleVoicemail(String channelId) {
        metrics.voicemailDetected();
        dialogRouter.notifyVoicemail(channelId);
        callExecutor.execute(() -> withMdc(channelId, () -> hangup(channelId)));
    }

    private Scenario resolveManualScenario(long companyId, String channelId) {
        ScenarioDefinition adHoc = pendingManualDefinitions.remove(channelId);
        if (adHoc != null) {
            return new Scenario(0, "adhoc-test-call", 0, "Ad-hoc test call", null, false, false, adHoc,
                    Instant.now(), null);
        }
        Long requested = pendingManualScenarios.remove(channelId);
        return requested != null && requested != NO_EXPLICIT_SCENARIO
                ? scenarioService.requireScenario(companyId, requested)
                : scenarioService.requireScenarioByKey(defaultTestScenarioKey());
    }

    private String defaultTestScenarioKey() {
        TestContextProperties t = dialogProperties.testContext();
        return t != null ? t.scenarioKey() : "debt-collection";
    }

    private String extractDid(Channel channel) {
        if (channel != null && channel.getDialplan() != null && channel.getDialplan().getExten() != null
                && !channel.getDialplan().getExten().isBlank()) {
            return channel.getDialplan().getExten();
        }
        return channel != null ? trunkOf(channel.getName()) : null;
    }

    private static String extractCallerNumber(Channel channel) {
        if (channel == null || channel.getCaller() == null) {
            return null;
        }
        String number = channel.getCaller().getNumber();
        return number != null && !number.isBlank() ? number : null;
    }

    private void playFallbackAndHangup(String channelId, InboundRoute route) {
        if (!ttsProperties.enabled()) {
            log.warn("TTS disabled; cannot play fallback message for {}", channelId);
            hangup(channelId);
            return;
        }
        ARI current = requireConnection();
        int port = portAllocator.allocate();
        RtpEndpoint endpoint = null;
        try {
            Files.createDirectories(Path.of(rtpProperties.recordingDir()));
            Path wav = Path.of(rtpProperties.recordingDir(), channelId.replace('/', '_') + "-fallback.wav");
            endpoint = new RtpEndpoint(port, rtpProperties.codec(),
                    new WavRecorder(wav, SAMPLE_RATE, rtpProperties.recordingMode()), List.of());
            endpoint.bind(rtpEventLoopGroup);

            Channel extMedia = current.channels()
                    .externalMedia(asteriskProperties.appName(), rtpProperties.localIp() + ":" + port, rtpProperties.codec().asteriskFormat())
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute();
            pointPlaybackAt(current, extMedia.getId(), endpoint);

            Bridge bridge = current.bridges().create().setType("mixing").execute();
            current.bridges().addChannel(bridge.getId(), extMedia.getId()).execute();

            // The route no longer carries a language of its own: outside business hours no
            // agent speaks, so the platform default is the only thing left to say it in.
            String language = ttsProperties.defaultLanguage();
            // Synthesis first, answer second: the caller hears ringback while the message is
            // rendered instead of an open line playing nothing.
            short[] pcm = ttsRouter.synthesize(route.fallbackMessage(), language, null);

            answer(channelId);
            current.bridges().addChannel(bridge.getId(), channelId).execute();
            endpoint.playPcm(pcm);
            awaitPlaybackDone(endpoint, pcm.length);

            current.channels().hangup(extMedia.getId()).execute();
            current.bridges().destroy(bridge.getId()).execute();
        } catch (Exception e) {
            log.warn("Failed to play fallback message for {}: {}", channelId, e.getMessage());
        } finally {
            if (endpoint != null) {
                endpoint.close();
            }
            portAllocator.release(port);
            hangup(channelId);
        }
    }

    private void awaitPlaybackDone(RtpEndpoint endpoint, int sampleCount) {
        long budgetMs = sampleCount * 1000L / SAMPLE_RATE + 2000;
        long deadline = System.currentTimeMillis() + budgetMs;
        while (endpoint.isPlaying() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean withinBusinessHours(InboundRoute route) {
        LocalTime start = route.businessHoursStart();
        LocalTime end = route.businessHoursEnd();
        if (start == null || end == null) {
            return true;
        }
        LocalTime now = LocalTime.now(clock);
        return !now.isBefore(start) && now.isBefore(end);
    }

    private CallContext buildTestContext(ScenarioDefinition scenario) {
        TestContextProperties t = dialogProperties.testContext();
        if (t == null) {
            return new CallContext(Map.of(), null);
        }
        Map<String, Object> facts = new HashMap<>();
        putIfDeclared(facts, scenario, "clientName", t.clientName());
        putIfDeclared(facts, scenario, "debtAmount", t.debtAmount());
        putIfDeclared(facts, scenario, "currency", t.currency());
        putIfDeclared(facts, scenario, "contractNumber", t.contractNumber());
        putIfDeclared(facts, scenario, "penaltyAmount", t.penaltyAmount());
        putIfDeclared(facts, scenario, "contractCancelDays", t.contractCancelDays());
        if (t.dueDate() != null && !t.dueDate().isBlank()) {
            try {
                putIfDeclared(facts, scenario, "dueDate", LocalDate.parse(t.dueDate()));
            } catch (Exception e) {
                log.warn("Invalid voice-agent.dialog.test-context.due-date '{}': {}", t.dueDate(), e.getMessage());
            }
        }
        return new CallContext(facts, t.goal());
    }

    private static void putIfDeclared(Map<String, Object> facts, ScenarioDefinition scenario,
                                      String factName, Object value) {
        if (value == null || scenario.factSchema() == null) {
            return;
        }
        boolean declared = scenario.factSchema().stream().anyMatch(f -> f.name().equals(factName));
        if (declared) {
            facts.put(factName, value);
        }
    }

    private void rememberCause(Channel channel, Integer cause) {
        if (channel == null || cause == null) {
            return;
        }
        Integer previous = hangupCauses.putIfAbsent(channel.getId(), cause);
        if (previous == null) {
            log.debug("Hangup cause for {}: {}", channel.getId(), HangupCause.label(cause));
        }
    }

    private void handleChannelDestroyed(ChannelDestroyed event) {
        Channel channel = event.getChannel();
        if (channel == null) {
            return;
        }
        String channelId = channel.getId();
        String name = channel.getName();
        if (name != null && name.startsWith(EXTERNAL_MEDIA_PREFIX)) {
            hangupCauses.remove(channelId);
            return;
        }
        rememberCause(channel, event.getCause());
        Integer cause = hangupCauses.remove(channelId);

        callRecordService.recordHangupCause(channelId, HangupCause.label(cause));

        Disposition disposition = HangupCause.toDisposition(cause);
        if (disposition == null) {
            disposition = Disposition.NO_ANSWER;
        }
        callRecordService.finishUnansweredAttempt(channelId, disposition);
        pendingManualScenarios.remove(channelId);
        pendingManualDefinitions.remove(channelId);
        webTestCalls.remove(channelId);

        OutboundCall outbound = outboundRegistry.removeIfUnanswered(channelId);
        if (outbound == null) {
            return;
        }
        dialerState.release(outbound.companyId());
        campaignService.applyOutcome(outbound.companyId(), outbound.targetId(), disposition);
        metrics.disposition(disposition);
        log.info("Unanswered call {} to {} settled as {} (cause {})",
                channelId, outbound.phone(), disposition, HangupCause.label(cause));
    }

    private void handleStasisEnd(StasisEnd event) {
        String channelId = event.getChannel().getId();
        DialogOutcome outcome = dialogRouter.outcome(channelId);
        DialogTechnicalSnapshot technical = dialogRouter.technicalSnapshot(channelId);
        dialogRouter.endCall(channelId);
        CallSession session = sessions.remove(channelId);
        if (session == null) {
            return;
        }
        Integer cause = hangupCauses.get(channelId);
        log.info("StasisEnd: channel {} — tearing down media (cause {})",
                channelId, HangupCause.label(cause));
        callExecutor.execute(() -> withMdc(channelId, () -> teardown(session, outcome, cause, technical)));
    }

    private void teardown(CallSession session, DialogOutcome outcome, Integer cause, DialogTechnicalSnapshot technical) {
        Disposition disposition = outcome.disposition();
        if (disposition == null) {
            disposition = HangupCause.toDisposition(cause);
            if (disposition != null) {
                log.info("[{}] no dialog outcome; hangup cause {} -> {}",
                        session.channelId(), HangupCause.label(cause), disposition);
            }
        }
        session.endpoint().close();
        reportRtpQuality(session);
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

        metrics.activeDec();
        routeRegistry.unregister(session.channelId());

        webTestCalls.remove(session.channelId());
        OutboundCall outbound = outboundRegistry.remove(session.channelId());
        long clientId = 0L;
        if (outbound != null) {
            clientId = outbound.clientId() != null ? outbound.clientId() : 0L;
            dialerState.release(outbound.companyId());
            if (disposition == Disposition.DO_NOT_CALL) {
                doNotCallRepository.add(outbound.companyId(), outbound.phone(), outcome.doNotCallReason(), DoNotCallSource.CALL);
            }
        }

        if (session.callAttemptId() != 0) {
            // Finalization has the last word on the disposition: it runs the summary,
            // which can recognise an outcome the caller hung up before a tool could
            // record. Applying the target's outcome first rescheduled such a call.
            disposition = callFinalizer.finalizeCall(session.callAttemptId(), clientId, session.scenarioId(),
                    Path.of(session.wavPath()), session.startedAt(), disposition,
                    session.channelName(), session.trunk(), technical);
        }
        if (outbound != null) {
            campaignService.applyOutcome(outbound.companyId(), outbound.targetId(), disposition);
            // After finalization, not before: the summary can recognise a promise the caller
            // hung up on before a tool recorded it, and that promise is a conversion.
            if (outbound.variantId() != null && disposition != null && disposition.isConversion()) {
                campaignVariants.recordConversion(outbound.variantId());
            }
        }
    }

    private void reportRtpQuality(CallSession session) {
        RtpStats stats = session.endpoint().stats();
        long received = stats.receivedPackets();
        metrics.recordRtpQuality(received, stats.lostPackets(), stats.reorderedPackets(), stats.jitterMillis());
        if (received == 0) {
            log.error("ALERT: no inbound RTP at all on call {} — the caller was never heard. "
                            + "Check that RTP_LOCAL_IP ({}) is reachable from Asterisk (docs/NETWORK.md)",
                    session.channelId(), rtpProperties.localIp());
            return;
        }
        double loss = stats.lossPercent();
        if (loss >= POOR_RTP_LOSS_PERCENT) {
            log.warn("[{}] poor inbound RTP: {}% loss ({} lost / {} received), jitter {}ms, {} out of order",
                    session.channelId(), Math.round(loss), stats.lostPackets(), received,
                    Math.round(stats.jitterMillis()), stats.reorderedPackets());
        } else {
            log.info("[{}] inbound RTP: {} packets, {}% loss, jitter {}ms",
                    session.channelId(), received, Math.round(loss), Math.round(stats.jitterMillis()));
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 30_000)
    public void reapGhostCalls() {
        if (sessions.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        int maxCallSeconds = dialogProperties != null && dialogProperties.maxCallSeconds() > 0
                ? dialogProperties.maxCallSeconds()
                : 300;
        int ghostTimeoutSeconds = maxCallSeconds + 60;

        for (CallSession session : sessions.values()) {
            if (session.startedAt() != null) {
                long elapsed = java.time.Duration.between(session.startedAt(), now).getSeconds();
                if (elapsed > ghostTimeoutSeconds) {
                    log.error("[{}] GHOST CALL DETECTED: call active for {}s (limit {}s) without StasisEnd. Forcing teardown.",
                            session.channelId(), elapsed, ghostTimeoutSeconds);
                    try {
                        hangup(session.channelId());
                    } catch (Exception e) {
                        log.debug("[{}] Ghost call hangup attempt failed: {}", session.channelId(), e.getMessage());
                    }
                    DialogOutcome outcome = dialogRouter.outcome(session.channelId());
                    DialogTechnicalSnapshot technical = dialogRouter.technicalSnapshot(session.channelId());
                    dialogRouter.endCall(session.channelId());
                    CallSession removed = sessions.remove(session.channelId());
                    if (removed != null) {
                        Integer cause = hangupCauses.get(session.channelId());
                        callExecutor.execute(() -> withMdc(session.channelId(), () -> teardown(removed, outcome, cause, technical)));
                    }
                }
            }
        }
    }

    public void hangupChannel(String channelId) {
        hangup(channelId);
    }

    /**
     * Sends keypad tones down an established call — the bot pressing buttons, not the client.
     *
     * <p>Outbound calls to a company reach an automated menu before they reach a person
     * ("buxgalteriya uchun 1 ni bosing"). Without this the call stops there: nobody is
     * listening for the digit and the menu times out. The agent hears the menu through the
     * same STT as any other speech and calls {@code sendDtmfTones} once it knows which
     * option matches (see {@code DialogTools}); this puts that digit on the line.
     *
     * <p>The timings are Asterisk's defaults made explicit: 100 ms per tone with 100 ms of
     * silence between, which is what carrier IVRs expect. Anything shorter is dropped by
     * some gateways.
     *
     * @param digits {@code 0-9}, {@code *}, {@code #} — anything else is rejected rather
     *               than sent, because a stray character makes Asterisk fail the whole request
     */
    public void sendDtmf(String channelId, String digits) {
        String cleaned = cleanDtmf(digits);
        if (cleaned.isEmpty()) {
            log.warn("[{}] refusing to send DTMF: '{}' has no dialable digits", channelId, digits);
            return;
        }
        try {
            requireConnection().channels().sendDTMF(channelId)
                    .setDtmf(cleaned)
                    .setDuration(DTMF_TONE_MS)
                    .setBetween(DTMF_GAP_MS)
                    .execute();
            log.info("[{}] sent DTMF '{}'", channelId, cleaned);
        } catch (Exception e) {
            throw new ExternalServiceException(ErrorCode.ARI_DTMF_FAILED, "asterisk", e, cleaned, channelId);
        }
    }

    /** Keeps only what a keypad can actually produce; everything else would fail the ARI request. */
    private static String cleanDtmf(String digits) {
        if (digits == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (char c : digits.toCharArray()) {
            if ((c >= '0' && c <= '9') || c == '*' || c == '#') {
                kept.append(c);
            }
        }
        return kept.length() > MAX_DTMF_DIGITS ? kept.substring(0, MAX_DTMF_DIGITS) : kept.toString();
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
            throw new ExternalServiceException(ErrorCode.ARI_NOT_CONNECTED, "asterisk");
        }
        return current;
    }

    @PreDestroy
    public void shutdown() {
        sessions.values().forEach(session -> {
            try {
                teardown(session, dialogRouter.outcome(session.channelId()),
                        hangupCauses.get(session.channelId()),
                        dialogRouter.technicalSnapshot(session.channelId()));
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
