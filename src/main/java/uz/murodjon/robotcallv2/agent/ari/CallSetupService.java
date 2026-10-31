package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.ARI;
import ch.loway.oss.ari4java.generated.models.Bridge;
import ch.loway.oss.ari4java.generated.models.Channel;
import ch.loway.oss.ari4java.generated.models.StasisStart;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.EventLoopGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.CallerIdPoolUseCase;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.audio.LiveAudioMonitor;
import uz.murodjon.robotcallv2.agent.dialog.*;
import uz.murodjon.robotcallv2.agent.metrics.CallMetrics;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeAudioBridge;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.agent.rtp.RtpPortAllocator;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.WavRecorder;
import uz.murodjon.robotcallv2.agent.session.CallMdc;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AgentWebhookUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.entity.CallAttempt;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignVariantUseCase;
import uz.murodjon.robotcallv2.crm.application.port.input.CrmUseCase;
import uz.murodjon.robotcallv2.crm.domain.entity.CrmClientSnapshot;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.dialer.application.port.input.OutboundCallTrackingUseCase;
import uz.murodjon.robotcallv2.dialer.domain.service.CallContextFactory;
import uz.murodjon.robotcallv2.inbound.domain.entity.InboundRoute;
import uz.murodjon.robotcallv2.memory.application.port.input.ClientMemoryUseCase;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.scenario.application.port.input.FactWebhookUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.FactWebhookRequest;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.CallLanguage;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * The start of a call (PROJECT.md §8): {@code StasisStart} routes the channel — a PBX leg,
 * a browser test, an inbound call, a call we placed — and for a call an AI agent takes, sets
 * up its media before the line opens, answers it, and starts the dialog.
 */
@Service
class CallSetupService {

    private static final Logger log = LoggerFactory.getLogger(CallSetupService.class);

    /** First Stasis argument of a browser test call (extensions.conf, extension 700). */
    private static final String WEB_TEST_ARG = "web-test";
    /** First Stasis argument of a call from the trunk (extensions.conf [from-trunk]); the second is the DID. */
    private static final String INBOUND_ARG = "inbound";

    private final ExecutorService callExecutor;
    private final AriClient ariClient;
    private final PbxLegService pbxLegService;
    private final PendingCallRegistry pendingCallRegistry;
    private final CallSessionRegistry callSessionRegistry;
    private final InboundCallRouter inboundCallRouter;
    private final AiAgentUseCase aiAgentService;
    /** Refreshes an inbound call's facts from the company's own system before the line is answered. */
    private final FactWebhookUseCase factWebhookUseCase;
    private final RtpProperties rtpProperties;
    private final RtpPortAllocator portAllocator;
    private final EventLoopGroup rtpEventLoopGroup;
    private final AsteriskProperties asteriskProperties;
    private final CallMediaFactory callMediaFactory;
    private final OutboundCallTrackingUseCase outboundCallTrackingUseCase;
    private final DialogProperties dialogProperties;
    private final WebTestCallService webTestCallService;
    private final CrmUseCase crmUseCase;
    private final ClientMemoryUseCase clientMemoryUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    /** A/B counters for the campaign variant an outbound call was assigned (§ campaign variants). */
    private final CampaignVariantUseCase campaignVariantUseCase;
    private final DialogStarter dialogStarter;
    private final DialogEngine dialogEngine;
    private final AudioPipelineAssembler audioPipelineAssembler;
    private final CallMetrics metrics;
    private final CallRouteRegistry routeRegistry;
    private final CallTeardownService callTeardownService;
    private final DialogRouter dialogRouter;
    private final ActiveCallService activeCallService;
    private final AgentWebhookUseCase agentWebhookUseCase;
    private final PreCallWorkflowRunner preCallWorkflowRunner;
    private final OperatorTransferService operatorTransferService;
    private final UnansweredCallSettler unansweredCallSettler;
    private final ObjectMapper objectMapper;
    private final PreConversationAudio preConversationAudio;
    private final ObjectProvider<CallerIdPoolUseCase> callerIdPoolUseCaseProvider;

    CallSetupService(ExecutorService callExecutor,
                     AriClient ariClient,
                     PbxLegService pbxLegService,
                     PendingCallRegistry pendingCallRegistry,
                     CallSessionRegistry callSessionRegistry,
                     InboundCallRouter inboundCallRouter,
                     AiAgentUseCase aiAgentService,
                     FactWebhookUseCase factWebhookUseCase,
                     RtpProperties rtpProperties,
                     RtpPortAllocator portAllocator,
                     EventLoopGroup rtpEventLoopGroup,
                     AsteriskProperties asteriskProperties,
                     CallMediaFactory callMediaFactory,
                     OutboundCallTrackingUseCase outboundCallTrackingUseCase,
                     DialogProperties dialogProperties,
                     WebTestCallService webTestCallService,
                     CrmUseCase crmUseCase,
                     ClientMemoryUseCase clientMemoryUseCase,
                     CallAttemptUseCase callAttemptUseCase,
                     CampaignVariantUseCase campaignVariantUseCase,
                     DialogStarter dialogStarter,
                     DialogEngine dialogEngine,
                     AudioPipelineAssembler audioPipelineAssembler,
                     CallMetrics metrics,
                     CallRouteRegistry routeRegistry,
                     CallTeardownService callTeardownService,
                     DialogRouter dialogRouter,
                     ActiveCallService activeCallService,
                     AgentWebhookUseCase agentWebhookUseCase,
                     PreCallWorkflowRunner preCallWorkflowRunner,
                     OperatorTransferService operatorTransferService,
                     UnansweredCallSettler unansweredCallSettler,
                     ObjectMapper objectMapper,
                     PreConversationAudio preConversationAudio,
                     ObjectProvider<CallerIdPoolUseCase> callerIdPoolUseCaseProvider) {
        this.objectMapper = objectMapper;
        this.preConversationAudio = preConversationAudio;
        this.callerIdPoolUseCaseProvider = callerIdPoolUseCaseProvider;
        this.callExecutor = callExecutor;
        this.ariClient = ariClient;
        this.pbxLegService = pbxLegService;
        this.pendingCallRegistry = pendingCallRegistry;
        this.callSessionRegistry = callSessionRegistry;
        this.inboundCallRouter = inboundCallRouter;
        this.aiAgentService = aiAgentService;
        this.factWebhookUseCase = factWebhookUseCase;
        this.rtpProperties = rtpProperties;
        this.portAllocator = portAllocator;
        this.rtpEventLoopGroup = rtpEventLoopGroup;
        this.asteriskProperties = asteriskProperties;
        this.callMediaFactory = callMediaFactory;
        this.outboundCallTrackingUseCase = outboundCallTrackingUseCase;
        this.dialogProperties = dialogProperties;
        this.webTestCallService = webTestCallService;
        this.crmUseCase = crmUseCase;
        this.clientMemoryUseCase = clientMemoryUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.campaignVariantUseCase = campaignVariantUseCase;
        this.dialogStarter = dialogStarter;
        this.dialogEngine = dialogEngine;
        this.audioPipelineAssembler = audioPipelineAssembler;
        this.metrics = metrics;
        this.routeRegistry = routeRegistry;
        this.callTeardownService = callTeardownService;
        this.dialogRouter = dialogRouter;
        this.activeCallService = activeCallService;
        this.agentWebhookUseCase = agentWebhookUseCase;
        this.preCallWorkflowRunner = preCallWorkflowRunner;
        this.operatorTransferService = operatorTransferService;
        this.unansweredCallSettler = unansweredCallSettler;
    }

    /** Runs on the channel's event lane. */
    void onStasisStart(StasisStart event) {
        Channel channel = event.getChannel();
        String channelId = channel.getId();
        String name = channel.getName();
        if (ChannelNames.isExternalMedia(name)) {
            log.debug("Ignoring externalMedia StasisStart for {} ({})", channelId, name);
            return;
        }
        List<String> args = event.getArgs() != null ? event.getArgs() : List.of();
        String kind = args.isEmpty() ? null : args.get(0);
        String argument = args.size() > 1 ? args.get(1) : null;
        if ("snoop".equals(kind)) {
            return; // a supervisor's listening leg — SupervisorMonitor bridges it
        }
        if (pbxLegService.routePbxLeg(kind, argument, args, channel)) {
            return;
        }
        if (WEB_TEST_ARG.equals(kind) && !pendingCallRegistry.claimWebTest(channelId, argument)) {
            ariClient.hangup(channelId);
            return;
        }
        log.info("StasisStart: channel {} ({}) args {}", channelId, name, args);
        String inboundDid = INBOUND_ARG.equals(kind) ? argument : null;
        if (!callSessionRegistry.markSettingUp(channelId)) {
            log.debug("Ignoring duplicate StasisStart for channel {}", channelId);
            return;
        }
        callExecutor.execute(() -> CallMdc.with(channelId, () -> {
            try {
                setupMedia(channelId, name, channel, inboundDid);
            } finally {
                callSessionRegistry.finishSettingUp(channelId);
            }
        }));
    }

    /**
     * Hands a channel already in Stasis and answered — a predictive client no operator took
     * (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1.4) — to {@code call}'s agent, as if it had been
     * dialled for it: from here on the call is an outbound AI call, ended and settled by teardown.
     */
    void startOutboundAiCall(String channelId, String channelName, OutboundCall call) {
        if (!callSessionRegistry.markSettingUp(channelId)) {
            log.debug("Ignoring duplicate outbound setup for channel {}", channelId);
            return;
        }
        outboundCallTrackingUseCase.register(channelId, call);
        callExecutor.execute(() -> CallMdc.with(channelId, () -> {
            try {
                setupMedia(channelId, channelName, null, null);
            } finally {
                callSessionRegistry.finishSettingUp(channelId);
            }
        }));
    }

    private void setupMedia(String channelId, String channelName, Channel channel, String inboundDid) {
        OutboundCall outbound = pendingCallRegistry.findOutboundProfile(channelId);
        boolean manual = pendingCallRegistry.isManual(channelId);
        InboundRoute inboundRoute = null;
        if (outbound == null && !manual) {
            inboundRoute = inboundCallRouter.routeInbound(channelId, channelName, channel, inboundDid);
            if (inboundRoute == null) {
                return;
            }
        }

        ARI current = ariClient.requireConnection();
        int port = -1;
        RtpEndpoint endpoint = null;
        // Asterisk-side legs, kept here so the catch below can release them: they are
        // created well before the session that teardown would otherwise clean up exists.
        String extMediaId = null;
        String bridgeId = null;
        long attemptId = 0;
        // From registration on, the resources are the teardown's to release, never the catch's.
        boolean registered = false;
        try {
            // Inside the try: running out of ports is one more way setup fails, and it has
            // to hang the caller up like the rest instead of escaping into the executor.
            port = portAllocator.allocate();
            // Ahead of the media and ahead of the answer, because this is the only window an
            // inbound call has for it. The caller is still hearing ringback here, so a slow
            // endpoint costs a moment of ringing; asked after the answer it would be a
            // silence on an open line — the very thing the ordering below exists to remove.
            // Outbound calls are served earlier still, in CallTaskConsumer, before dialling.
            Scenario inboundScenario = null;
            AiAgent inboundAgent = null;
            String inboundFacts = null;
            if (inboundRoute != null) {
                inboundAgent = aiAgentService.requireCallAgent(inboundRoute.companyId(), inboundRoute.aiAgentId(),
                        ChannelNames.extractCallerNumber(channel));
                inboundScenario = aiAgentService.resolveScenario(inboundRoute.companyId(), inboundAgent);
                if (inboundScenario != null && inboundScenario.definition() != null && inboundScenario.definition().factWebhook() != null) {
                    inboundFacts = factWebhookUseCase.fetchFacts(inboundScenario.definition().factWebhook(),
                            FactWebhookRequest.inbound(ChannelNames.extractCallerNumber(channel), ChannelNames.extractDid(channel)));
                }
            }

            AiAgent manualAgent = manual ? pendingCallRegistry.takeManualAgent(channelId) : null;

            AiAgent setupAgent = outbound != null ? outbound.agent() : inboundAgent != null ? inboundAgent : manualAgent;
            boolean shouldRecord = setupAgent == null || setupAgent.dataPolicy().keepsCallAudio();
            WavRecorder recorder = null;
            String wavPath = null;
            if (shouldRecord) {
                Files.createDirectories(Path.of(rtpProperties.recordingDir()));
                Path wav = Path.of(rtpProperties.recordingDir(), channelId.replace('/', '_') + ".wav");
                recorder = new WavRecorder(wav, CallMediaFactory.SAMPLE_RATE, rtpProperties.recordingMode());
                wavPath = wav.toString();
            }

            // Media before the line opens. Answering first and then looking the caller up
            // left the call connected with no RTP path for as long as that lookup took —
            // a CRM HTTP round trip on an inbound call — and everything said into that gap
            // was lost. Here the socket is bound, externalMedia is up and Asterisk has been
            // told where to send audio before anyone can speak; the listener chain that
            // needs the database is swapped in afterwards (RtpEndpoint#replaceListeners).
            LiveAudioMonitor audioMonitor = new LiveAudioMonitor(rtpEventLoopGroup);
            endpoint = new RtpEndpoint(channelId, port, rtpProperties.codec(), recorder,
                    List.<AudioListener>of(audioMonitor), audioMonitor::onBotAudio, rtpProperties.receiveQueueCapacity(),
                rtpProperties.maxQueuedPlaybackSeconds());
            endpoint.bind(rtpEventLoopGroup);

            Channel extMedia = current.channels()
                    .externalMedia(asteriskProperties.appName(), rtpProperties.localIp() + ":" + port, rtpProperties.codec().asteriskFormat())
                    .setEncapsulation("rtp")
                    .setTransport("udp")
                    .execute();
            extMediaId = extMedia.getId();
            callMediaFactory.pointPlaybackAt(current, extMedia.getId(), endpoint);

            Bridge bridge = current.bridges().create().setType("mixing").execute();
            bridgeId = bridge.getId();
            current.bridges().addChannel(bridge.getId(), extMedia.getId()).execute();

            // Only now. An unanswered channel added to a bridge is answered implicitly, so
            // the explicit answer has to come first for the ordering above to mean anything.
            ariClient.answer(channelId);
            ariClient.enableJitterBuffer(current, channelId);
            current.bridges().addChannel(bridge.getId(), channelId).execute();

            long targetId;
            String language;
            String ttsVoice;
            CallContext context;
            Scenario scenarioRow;
            String phone;
            long companyId;
            // Null only on a manual test call placed without agentId, which runs a scenario with
            // no agent behind it and therefore falls back to the platform defaults everywhere below.
            AiAgent agent;

            if (outbound != null) {
                agent = outbound.agent();
                targetId = outbound.targetId();
                language = outbound.language() != null ? outbound.language() : dialogProperties.language();
                ttsVoice = outbound.ttsVoice();
                context = outbound.context();
                scenarioRow = aiAgentService.resolveScenario(outbound.companyId(), agent);
                phone = outbound.phone();
                companyId = outbound.companyId();
            } else if (manual) {
                agent = manualAgent;
                targetId = CallAttempt.NO_TARGET;
                language = agent != null ? agent.language() : dialogProperties.language();
                ttsVoice = agent != null ? agent.voiceFor(language) : dialogProperties.ttsVoice();
                Long manualCompanyId = pendingCallRegistry.takeManualCompany(channelId);
                if (manualCompanyId == null) {
                    // Every manual and test call registers its company when it is placed; without
                    // one this call would run, and be billed, as the default company.
                    throw new ConflictException(ErrorCode.CALL_COMPANY_UNKNOWN, channelId);
                }
                companyId = manualCompanyId;
                scenarioRow = agent != null
                        ? aiAgentService.resolveScenario(companyId, agent)
                        : webTestCallService.resolveManualScenario(companyId, channelId);
                String manualPhone = pendingCallRegistry.takeManualPhone(channelId);
                phone = (manualPhone != null && !manualPhone.isBlank()) ? manualPhone : "MANUAL";
                CrmClientSnapshot crm = PhoneNumbers.isValid(phone)
                        ? crmUseCase.findByPhone(companyId, phone) : null;
                ClientMemory memory = PhoneNumbers.isValid(phone)
                        ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, phone) : null;
                CallContext testContext = webTestCallService.buildTestContext(scenarioRow.definition());
                context = CallContextFactory.mergeCrmFacts(testContext, crm).withMemory(memory);
            } else {
                // The same object an outbound call reads its voice and persona from. Before
                // the agent existed an inbound call had no campaign to read them from, so it
                // always answered as AI_ASSISTANT in the company's default voice.
                agent = inboundAgent;
                targetId = CallAttempt.NO_TARGET;
                scenarioRow = inboundScenario;
                String callerNumber = ChannelNames.extractCallerNumber(channel);
                phone = callerNumber != null ? callerNumber : "INBOUND";
                companyId = inboundRoute.companyId();
                CrmClientSnapshot crm = callerNumber != null
                        ? crmUseCase.findByPhone(companyId, callerNumber) : null;
                ClientMemory memory = callerNumber != null
                        ? clientMemoryUseCase.findByCompanyIdAndPhone(companyId, callerNumber) : null;
                // Read after the lookups, not before — a caller the company already knows to
                // be Russian-speaking used to be answered in the agent's language on every
                // inbound call, while the same person was called in Russian outbound.
                language = CallLanguage.resolve(
                        crm != null ? crm.preferredLanguage() : null,
                        memory != null ? memory.preferredLanguage() : null,
                        agent.language());
                ttsVoice = agent.voiceFor(language);
                // The webhook's answer goes on last, over the CRM: it was asked most recently.
                context = CallContextFactory.overlayWebhookFacts(objectMapper, CallContextFactory.mergeCrmFacts(new CallContext(Map.of(), null), crm).withMemory(memory),
                        inboundFacts, scenarioRow.definition().factSchema());
            }
            // The number on the other end, for the company tools that fill a phone from the call.
            // "MANUAL" and "INBOUND" (a withheld number) are not numbers and are left out.
            if (context != null && PhoneNumbers.isValid(phone)) {
                context = context.withPhone(phone);
            }

            Instant startedAt = Instant.now();
            attemptId = callAttemptUseCase.findAttemptIdByChannel(channelId);
            if (attemptId == 0) {
                attemptId = callAttemptUseCase.startAttempt(companyId, targetId, channelId, phone, language,
                        inboundRoute != null ? inboundRoute.storedId() : null,
                        outbound != null ? outbound.variantId() : null);
            }
            callAttemptUseCase.markAnswered(attemptId);
            // The A/B denominator. Dispatch counted the attempt; this counts the ones that
            // reached a human, which is the only population a conversion rate means anything
            // over — a variant is not worse because its half of the list picked up less.
            if (outbound != null && outbound.variantId() != null) {
                campaignVariantUseCase.recordAnswer(outbound.variantId());
            }

            PipelineMode pipelineMode = dialogStarter.resolveMode(channelId, agent,
                    outbound != null ? outbound.pipelineModeOverride() : null);
            boolean realtime = pipelineMode.usesRealtimeEngine();
            RealtimeAudioBridge realtimeBridge = realtime ? new RealtimeAudioBridge(pipelineMode == PipelineMode.REALTIME) : null;

            List<AudioListener> audioListeners = audioPipelineAssembler.buildAudioListeners(channelId, companyId, attemptId, startedAt,
                    language, realtimeBridge, context, agent, endpoint::recentOutboundLevel);
            audioListeners.add(audioMonitor);
            endpoint.replaceListeners(audioListeners);

            boolean active = callSessionRegistry.register(new CallSession(channelId, companyId, extMedia.getId(), bridge.getId(),
                    port, endpoint, attemptId, startedAt, wavPath, channelName, ChannelNames.extractTrunk(channelName),
                    scenarioRow.id(), audioMonitor, agent != null && agent.callBehaviour().dtmfInputEnabled(), agent));
            registered = true;
            if (outbound != null) {
                outboundCallTrackingUseCase.markAnswered(channelId);
                if (outbound.callerIdPoolId() != null) {
                    CallerIdPoolUseCase poolUseCase = callerIdPoolUseCaseProvider.getIfAvailable();
                    if (poolUseCase != null) {
                        String callerPhone = ChannelNames.extractCallerNumber(channel);
                        if (callerPhone != null && !callerPhone.isBlank()) {
                            try {
                                poolUseCase.recordCallAnswer(outbound.callerIdPoolId(), callerPhone);
                            } catch (Exception e) {
                                log.warn("[{}] failed to record caller ID answer: {}", channelId, e.getMessage());
                            }
                        }
                    }
                }
            }
            log.info("Media ready for {}: rtpPort={}, extMedia={}, bridge={}, wav={}, attempt={}",
                    channelId, port, extMedia.getId(), bridge.getId(), wavPath, attemptId);

            metrics.callStarted();
            metrics.activeInc();
            routeRegistry.register(channelId, companyId);

            if (!active) {
                // The caller hung up while this was being set up. StasisEnd found no session
                // then and left the teardown to this thread; the claim still decides, so a
                // reaper or shutdown that got there first tears it down instead.
                log.info("[{}] caller left during setup — tearing down before the dialog starts", channelId);
                try {
                    callTeardownService.tearDownIfClaimed(channelId, dialogRouter.outcome(channelId),
                            callSessionRegistry.findHangupCause(channelId),
                            dialogRouter.technicalSnapshot(channelId), false);
                } catch (Exception e) {
                    log.warn("[{}] teardown after an early hangup failed: {}", channelId, e.getMessage());
                }
                return;
            }

            String testFile = rtpProperties.testPlaybackFile();
            if (testFile != null && !testFile.isBlank()) {
                activeCallService.play(channelId, Path.of(testFile));
            }

            if (agent != null && !agent.preCallFunctions().isEmpty() && context != null) {
                // Before the initiation webhook, so the webhook sees what the lookups found.
                String preCallPhone = outbound != null ? outbound.phone() : ChannelNames.extractCallerNumber(channel);
                context = context.withMergedFacts(preCallWorkflowRunner.run(agent, companyId, channelId, attemptId,
                        language, preCallPhone, context.facts()));
            }

            if (agent != null && agent.initiationWebhook() != null && agentWebhookUseCase != null) {
                try {
                    String calleePhone = outbound != null ? outbound.phone() : ChannelNames.extractCallerNumber(channel);
                    var initResult = agentWebhookUseCase.executeInitiation(
                            companyId, attemptId, channelId, calleePhone,
                            agent, context != null ? context.facts() : Map.of());
                    if (initResult != null) {
                        if (initResult.variables() != null && !initResult.variables().isEmpty() && context != null) {
                            context = context.withMergedFacts(initResult.variables());
                        }
                        if (initResult.firstMessageOverride() != null && !initResult.firstMessageOverride().isBlank()) {
                            agent = agent.withSpokenOpening(initResult.firstMessageOverride());
                        }
                    }
                } catch (Exception e) {
                    log.warn("[{}] initiation webhook execution failed: {}", channelId, e.getMessage());
                }
            }

            boolean startDialog = !manual || dialogProperties.autoStart();
            // Keypad tones only on calls we placed. Inbound is a person who dialled us —
            // there is no menu on their end to navigate, and a bot pressing buttons into a
            // live caller's ear is only ever a bug.
            Consumer<String> dtmfSender = outbound != null ? digits -> ariClient.sendDtmf(channelId, digits) : null;
            // An A/B variant may replace the scenario's role prompt for this call only; the
            // scenario row itself is untouched, so the other variant's calls are unaffected.
            ScenarioDefinition definition = outbound != null
                    ? scenarioRow.definition().withRolePrompt(outbound.promptOverride())
                    : scenarioRow.definition();
            if (agent != null) {
                endpoint.setAmbientSound(agent.ambience().background(),
                        agent.ambience().backgroundVolume(),
                        agent.ambience().backgroundFadeInSeconds());
                endpoint.setThinkingSound(agent.ambience().thinking(), agent.ambience().thinkingVolume());
                endpoint.setNoiseCancellation(agent.ambience().noiseCancellationEnabled(), agent.ambience().noiseCancellationMode());
            }
            audioPipelineAssembler.attachDenoiser(endpoint);
            if (dialogProperties.enabled() && startDialog) {
                // Before the dialog, and before the check below: the caller may leave while it plays.
                preConversationAudio.play(channelId, companyId, agent, endpoint);
                // The test file and the initiation webhook take time, and StasisEnd may have
                // claimed and torn the call down meanwhile: a dialog started now would talk
                // to nobody until the reaper.
                if (!callSessionRegistry.isActive(channelId)) {
                    log.info("[{}] caller left during setup — the dialog is not started", channelId);
                    return;
                }
                CallStart start = new CallStart(channelId, endpoint, context, definition, language, ttsVoice, agent,
                        () -> ariClient.hangup(channelId), () -> operatorTransferService.transferToOperator(channelId),
                        attemptId, dtmfSender);
                if (realtime) {
                    boolean started = dialogStarter.startRealtime(start, pipelineMode, realtimeBridge,
                            reconnecting -> continueOnCascade(reconnecting, startedAt, audioMonitor));
                    if (!started) {
                        // Nothing has been said yet, so the caller simply gets the cascade
                        // pipeline's own opening instead of a dropped call.
                        log.warn("[{}] realtime dialog did not start — continuing on the cascade pipeline", channelId);
                        continueOnCascade(start.withDefaultVoice(), startedAt, audioMonitor);
                    }
                } else {
                    dialogEngine.startCall(start);
                }
                if (!callSessionRegistry.isActive(channelId)) {
                    // StasisEnd came between the check above and the start: its endCall ran
                    // before this dialog existed, so the dialog is ended here instead.
                    log.info("[{}] caller left while the dialog was starting — ending it", channelId);
                    dialogRouter.endCall(channelId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to set up media for {}: {}", channelId, e.getMessage(), e);
            callAttemptUseCase.recordError(attemptId, e.toString());
            // Whoever holds the session owns the cleanup. Once it is registered, the
            // hangup below produces a StasisEnd and teardown does all of this; doing it
            // here as well handed the same RTP port back twice. Between the two releases
            // that port can be allocated to another call, and the second release puts a
            // port that call is bound to back in the free set — the next call to draw it
            // then fails to bind. So: clean up only what teardown will never see — which is
            // everything until the session was registered, and nothing after, even once its
            // teardown has run and the session is gone again.
            if (!registered) {
                // Asterisk's legs first: left up, the externalMedia channel and the bridge
                // stayed in Asterisk until it restarted.
                if (extMediaId != null) {
                    try {
                        current.channels().hangup(extMediaId).execute();
                    } catch (Exception ex) {
                        log.debug("externalMedia channel {} already gone: {}", extMediaId, ex.getMessage());
                    }
                }
                if (bridgeId != null) {
                    try {
                        current.bridges().destroy(bridgeId).execute();
                    } catch (Exception ex) {
                        log.debug("Bridge {} already gone: {}", bridgeId, ex.getMessage());
                    }
                }
                if (endpoint != null) {
                    endpoint.close();
                }
                if (port >= 0) {
                    portAllocator.release(port);
                }
                OutboundCall failedOutbound = outboundCallTrackingUseCase.remove(channelId);
                if (failedOutbound != null) {
                    unansweredCallSettler.settleFailedOutbound(channelId, failedOutbound);
                }
            }
            ariClient.hangup(channelId);
        }
    }

    /**
     * Carry a call on the cascade pipeline after its realtime engine could not start, or
     * dropped mid-conversation and could not be resumed — rather than hanging up on a
     * caller who is still there.
     *
     * <p>The two pipelines listen through different chains (a realtime call has no STT
     * stream at all), so the endpoint's listeners are replaced, not added to. {@code start}
     * already carries the company default voice ({@link CallStart#withDefaultVoice}).
     */
    private void continueOnCascade(CallStart start, Instant startedAt, LiveAudioMonitor audioMonitor) {
        String channelId = start.channelId();
        RtpEndpoint endpoint = start.endpoint();
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null) {
            return; // torn down in the meantime — nobody left to talk to
        }
        if (!dialogEngine.available()) {
            log.error("[{}] realtime engine lost and the cascade pipeline is unavailable — hanging up", channelId);
            ariClient.hangup(channelId);
            return;
        }
        List<AudioListener> listeners;
        try {
            listeners = audioPipelineAssembler.buildAudioListeners(channelId, session.companyId(), start.callAttemptId(),
                    startedAt, start.language(), null, start.context(), start.agent(), endpoint::recentOutboundLevel);
        } catch (Exception e) {
            // The cascade needs speech recognition; without it there is nobody to talk to.
            log.error("[{}] realtime engine lost and the cascade could not start: {} — hanging up",
                    channelId, e.getMessage());
            ariClient.hangup(channelId);
            return;
        }
        listeners.add(audioMonitor);
        endpoint.replaceListeners(listeners);
        log.warn("[{}] continuing the call on the cascade pipeline", channelId);
        dialogEngine.startCall(start);
    }
}
