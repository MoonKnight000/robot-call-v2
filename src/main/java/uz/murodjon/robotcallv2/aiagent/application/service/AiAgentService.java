package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentScriptDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentSpeechEngineDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentVoiceDto;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.mapper.AiAgentSettingsMapper;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentCatalogUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AgentRolloutRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.event.AiAgentClonedEvent;
import uz.murodjon.robotcallv2.aiagent.domain.service.AgentRolloutRouter;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI agents — the voice, persona, model and speech engine a scenario is spoken with. This
 * service owns the agent's rows and lookups; its versions, section settings and catalog
 * live in {@link AiAgentVersionService}, {@link AiAgentSettingsService} and
 * {@link AiAgentCatalogService}.
 */
@Service
public class AiAgentService implements AiAgentUseCase {

    /** A group the request left out: every field null, so each one falls back like an omitted field. */
    private static final AgentScriptDto NO_SCRIPT = new AgentScriptDto(null, null, null, null, null, null);
    private static final AgentSpeechEngineDto NO_SPEECH_ENGINE =
            new AgentSpeechEngineDto(null, null, null, null, null, null, null, null, null);
    private static final AgentVoiceDto NO_VOICE = new AgentVoiceDto(null, null, null, null, null, null, null, null);
    /** What a new agent starts with where its request is silent: noise cancellation and keypad input on, data kept. */
    private static final AiAgentAmbience NEW_AMBIENCE = new AiAgentAmbience(null, null, null, null, null, true, null);
    private static final AiAgentCallBehaviour NEW_CALL_BEHAVIOUR =
            new AiAgentCallBehaviour(null, 0, true, null, null, false, null, null, null, null, null);
    private static final AiAgentDataPolicy NEW_DATA_POLICY = new AiAgentDataPolicy(false, true, null, null);

    private final AiAgentRepository repository;
    private final AiAgentVersionRepository versionRepository;
    private final AiAgentDrafts drafts;
    private final AiAgentEngineGuard engineGuard;
    private final ScenarioUseCase scenarioUseCase;
    private final CompanyConfigUseCase companyConfigUseCase;
    private final UserUseCase userUseCase;
    private final AuditUseCase auditUseCase;
    private final CurrentUser currentUser;
    /** Tells the features that keep something per agent — the tool links — that a clone was made. */
    private final ApplicationEventPublisher eventPublisher;
    /** Which callers a running rollout sends to its candidate (requireCallAgent). */
    private final AgentRolloutRepository rolloutRepository;
    private final AiAgentCatalogUseCase aiAgentCatalogUseCase;

    public AiAgentService(
            AiAgentRepository repository,
            AiAgentVersionRepository versionRepository,
            AiAgentDrafts drafts,
            AiAgentEngineGuard engineGuard,
            ScenarioUseCase scenarioUseCase,
            CompanyConfigUseCase companyConfigUseCase,
            UserUseCase userUseCase,
            AuditUseCase auditUseCase,
            CurrentUser currentUser,
            ApplicationEventPublisher eventPublisher,
            AgentRolloutRepository rolloutRepository,
            AiAgentCatalogUseCase aiAgentCatalogUseCase
    ) {
        this.rolloutRepository = rolloutRepository;
        this.aiAgentCatalogUseCase = aiAgentCatalogUseCase;
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.drafts = drafts;
        this.engineGuard = engineGuard;
        this.scenarioUseCase = scenarioUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
        this.userUseCase = userUseCase;
        this.auditUseCase = auditUseCase;
        this.currentUser = currentUser;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public AiAgentRow createAgent(long companyId, CreateAiAgentRequest request) {
        AgentScriptDto scriptRequest = request.script() != null ? request.script() : NO_SCRIPT;
        AgentSpeechEngineDto engineRequest = request.speechEngine() != null ? request.speechEngine() : NO_SPEECH_ENGINE;
        AgentVoiceDto voiceRequest = request.voice() != null ? request.voice() : NO_VOICE;
        AiAgentDrafts.requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        drafts.requireOwnQueue(companyId, request.callBehaviour() != null ? request.callBehaviour().transferQueueId() : null);
        drafts.requirePersonaAllowed(companyId, request.persona());

        // resolveLanguage already answers a blank request with the company's own default,
        // as its BCP-47 code. Deriving it here from the enum's name instead produced
        // "uz_uz", which is not a language code any recogniser or voice accepts.
        String language = companyConfigUseCase.resolveLanguage(companyId, request.language());

        // Auto-populate from template presets if templateId is specified and prompts are omitted
        TemplateDefaults defaults = (scriptRequest.templateId() != null)
                ? AiAgentTemplates.getPreset(scriptRequest.templateId(), language)
                : null;

        String firstMessage = (scriptRequest.firstMessage() != null && !scriptRequest.firstMessage().isBlank())
                ? scriptRequest.firstMessage().trim()
                : (defaults != null ? defaults.firstMessage() : null);

        String systemPrompt = (scriptRequest.systemPrompt() != null && !scriptRequest.systemPrompt().isBlank())
                ? scriptRequest.systemPrompt().trim()
                : (defaults != null ? defaults.systemPrompt() : null);

        List<DataExtractionField> dataNeeded = (request.dataNeeded() != null && !request.dataNeeded().isEmpty())
                ? request.dataNeeded()
                : (defaults != null ? defaults.dataNeeded() : List.of());

        List<DataEvaluationCriterion> dataEvaluation = (request.dataEvaluation() != null && !request.dataEvaluation().isEmpty())
                ? request.dataEvaluation()
                : (defaults != null ? defaults.dataEvaluation() : List.of());

        // The voice and model families depend on which pipeline this agent will run, so the
        // mode has to be settled before anything is checked against it.
        PipelineMode pipelineMode = engineRequest.mode() != null ? engineRequest.mode() : PipelineMode.CASCADE;
        // A profile fills only what the request left out, like a template does for the prompts.
        EngineProfile profile = request.engineProfileId() != null
                ? aiAgentCatalogUseCase.requireEngineProfile(request.engineProfileId()) : EngineProfile.NONE;
        String sttProvider = engineRequest.sttProvider() != null ? engineRequest.sttProvider() : profile.sttProvider();
        // The profile's recognizer model belongs to its own provider, so it never rides along
        // with a provider the request chose itself.
        String sttModel = engineRequest.sttModel() != null ? engineRequest.sttModel()
                : engineRequest.sttProvider() == null ? profile.sttModel() : null;
        String realtimeProvider = engineGuard.requireKnownRealtimeProvider(pipelineMode, engineRequest.realtimeProvider());
        String ttsVoice = engineGuard.requireKnownVoice(companyId, pipelineMode, voiceRequest.defaultVoice());
        String llmModel = engineGuard.requireKnownModel(pipelineMode, realtimeProvider,
                request.llmModel() != null ? request.llmModel() : profile.llmModel());
        String fastLlmModel = engineGuard.requireKnownFastModel(
                request.fastLlmModel() != null ? request.fastLlmModel() : profile.fastLlmModel());
        Map<String, String> checkedLanguageVoices =
                engineGuard.requireKnownVoicePerLanguage(companyId, pipelineMode, voiceRequest.perLanguage());

        if (scriptRequest.scenarioId() != null) {
            scenarioUseCase.requireScenario(companyId, scriptRequest.scenarioId());
        }

        // Everything the request left out is defaulted by the group it belongs to, so what
        // is written here is only what a create means beyond those defaults.
        Instant now = Instant.now();
        AiAgent agent = AiAgent.builder()
                .id(0L)
                .companyId(companyId)
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .language(language)
                .persona(request.persona())
                .script(new AiAgentScript(
                        scriptRequest.templateId(),
                        scriptRequest.scenarioId(),
                        scriptRequest.mode(),
                        scriptRequest.definition(),
                        firstMessage,
                        systemPrompt))
                .speechEngine(new AiAgentSpeechEngine(
                        pipelineMode,
                        realtimeProvider,
                        trimmed(engineRequest.pipecatStt()),
                        trimmed(engineRequest.pipecatLlm()),
                        trimmed(engineRequest.pipecatTts()),
                        engineGuard.requireKnownSttProvider(sttProvider),
                        engineGuard.requireKnownSttModel(sttProvider, sttModel),
                        engineGuard.requireKnownTtsProvider(engineRequest.ttsProvider()),
                        engineGuard.requireKnownTtsModel(engineRequest.ttsProvider(), engineRequest.ttsModel())))
                .voice(new AiAgentVoice(
                        ttsVoice,
                        voiceRequest.speed(),
                        voiceRequest.stability(),
                        voiceRequest.similarityBoost(),
                        checkedLanguageVoices,
                        voiceRequest.perLanguageSpeed(),
                        voiceRequest.emotionAdaptive() != null && voiceRequest.emotionAdaptive(),
                        voiceRequest.warmth()))
                .ambience(AiAgentSettingsMapper.toAiAgentAmbience(request.ambience(), NEW_AMBIENCE))
                .callBehaviour(AiAgentSettingsMapper.toAiAgentCallBehaviour(request.callBehaviour(), NEW_CALL_BEHAVIOUR))
                .limits(AiAgentSettingsMapper.toAiAgentLimits(request.limits(), AiAgentLimits.NONE))
                .dataPolicy(AiAgentSettingsMapper.toAiAgentDataPolicy(request.dataPolicy(), NEW_DATA_POLICY))
                .llmModel(llmModel)
                .fastLlmModel(fastLlmModel)
                .temperature(request.temperature() != null ? request.temperature() : profile.temperature())
                .maxOutputTokens(request.maxOutputTokens() != null ? request.maxOutputTokens() : profile.maxOutputTokens())
                .preemptiveGeneration(request.preemptiveGeneration() != null && request.preemptiveGeneration())
                .ivrNavigationEnabled(request.ivrNavigationEnabled() == null || request.ivrNavigationEnabled())
                .useRag(request.useRag() != null && request.useRag())
                .dataNeeded(dataNeeded)
                .dataEvaluation(dataEvaluation)
                .initiationWebhook(request.initiationWebhook())
                .postCallWebhook(request.postCallWebhook())
                .sipTrunkIds(request.sipTrunkIds())
                .enabled(request.enabled() == null || request.enabled())
                .createdAt(now)
                .build();

        AiAgentDrafts.requireCompatibleDataPolicy(agent.dataPolicy().zeroPiiRetention(), agent.dataPolicy().storeCallAudio());
        long id = repository.create(drafts.validate(companyId, agent));
        auditUseCase.record(companyId, "AI_AGENT_CREATE", "ai_agent", String.valueOf(id), agent.name());
        return findAgentRow(companyId, id);
    }

    @Override
    public AiAgentRow updateAgent(long companyId, long id, UpdateAiAgentRequest request) {
        AiAgent existing = requireAgent(companyId, id);
        AgentScriptDto scriptRequest = request.script() != null ? request.script() : NO_SCRIPT;
        AgentSpeechEngineDto engineRequest = request.speechEngine() != null ? request.speechEngine() : NO_SPEECH_ENGINE;
        AgentVoiceDto voiceRequest = request.voice() != null ? request.voice() : NO_VOICE;
        AiAgentDrafts.requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        drafts.requireOwnQueue(companyId, request.callBehaviour() != null ? request.callBehaviour().transferQueueId() : null);
        drafts.requirePersonaAllowed(companyId, request.persona());

        String language = (request.language() != null && !request.language().isBlank())
                ? companyConfigUseCase.resolveLanguage(companyId, request.language())
                : existing.language();

        // Checked against the mode the agent will have after this edit, not the one it had:
        // switching to REALTIME and picking a realtime voice arrives as a single request.
        PipelineMode pipelineMode = engineRequest.mode() != null
                ? engineRequest.mode()
                : existing.speechEngine().mode();

        String ttsVoice = (voiceRequest.defaultVoice() != null)
                ? engineGuard.requireKnownVoice(companyId, pipelineMode, voiceRequest.defaultVoice())
                : existing.voice().defaultVoice();

        // Same reason the mode is settled first: switching engine and model arrives as one
        // request, and the model has to be checked against the engine it will run on.
        String realtimeProvider = engineRequest.realtimeProvider() != null || engineRequest.mode() != null
                ? engineGuard.requireKnownRealtimeProvider(pipelineMode, engineRequest.realtimeProvider() != null
                        ? engineRequest.realtimeProvider() : existing.speechEngine().realtimeProvider())
                : existing.speechEngine().realtimeProvider();

        // A profile picked in this edit replaces the agent's engine choices; a field the request
        // names itself still wins over the profile's.
        EngineProfile profile = request.engineProfileId() != null
                ? aiAgentCatalogUseCase.requireEngineProfile(request.engineProfileId()) : null;

        String requestedLlmModel = chooseValue(request.llmModel(), profile, EngineProfile::llmModel);
        String llmModel = (requestedLlmModel != null)
                ? engineGuard.requireKnownModel(pipelineMode, realtimeProvider, requestedLlmModel)
                : existing.llmModel();

        String requestedFastLlmModel = chooseValue(request.fastLlmModel(), profile, EngineProfile::fastLlmModel);
        String fastLlmModel = (requestedFastLlmModel != null)
                ? engineGuard.requireKnownFastModel(requestedFastLlmModel)
                : existing.fastLlmModel();
        Double temperature = chooseValue(request.temperature(), profile, EngineProfile::temperature);
        Integer maxOutputTokens = chooseValue(request.maxOutputTokens(), profile, EngineProfile::maxOutputTokens);

        Map<String, String> languageVoices = (voiceRequest.perLanguage() != null)
                ? engineGuard.requireKnownVoicePerLanguage(companyId, pipelineMode, voiceRequest.perLanguage())
                : existing.voice().perLanguage();

        Long scenarioId = existing.script().scenarioId();
        if (scriptRequest.scenarioId() != null) {
            scenarioUseCase.requireScenario(companyId, scriptRequest.scenarioId());
            scenarioId = scriptRequest.scenarioId();
        }

        AiAgentScript script = existing.script();
        AiAgentSpeechEngine speechEngine = existing.speechEngine();
        // The model is checked against the provider it will actually run on, which is the
        // one this request leaves behind — changing only the model must not be validated
        // against a provider the agent no longer has, nor the other way round.
        String requestedSttProvider = chooseValue(engineRequest.sttProvider(), profile, EngineProfile::sttProvider);
        String sttProvider = requestedSttProvider != null
                ? engineGuard.requireKnownSttProvider(requestedSttProvider) : speechEngine.sttProvider();
        // The profile's recognizer model belongs to its own provider, so it never rides along
        // with a provider the request chose itself.
        String requestedSttModel = engineRequest.sttModel() != null ? engineRequest.sttModel()
                : engineRequest.sttProvider() == null && profile != null ? profile.sttModel() : null;
        String ttsProvider = engineRequest.ttsProvider() != null
                ? engineGuard.requireKnownTtsProvider(engineRequest.ttsProvider()) : speechEngine.ttsProvider();
        AiAgentVoice voice = existing.voice();

        // The pronunciation rules, post-call actions, creator, creation time and published version
        // stay the existing agent's: this request does not carry them.
        AiAgent updated = existing.toBuilder()
                .name(request.name() != null ? request.name().trim() : existing.name())
                .description(request.description() != null ? request.description().trim() : existing.description())
                .language(language)
                .persona(request.persona() != null ? request.persona() : existing.persona())
                .script(new AiAgentScript(
                        scriptRequest.templateId() != null ? scriptRequest.templateId() : script.templateId(),
                        scenarioId,
                        scriptRequest.mode() != null ? scriptRequest.mode() : script.mode(),
                        scriptRequest.definition() != null ? scriptRequest.definition() : script.definition(),
                        scriptRequest.firstMessage() != null ? scriptRequest.firstMessage().trim() : script.firstMessage(),
                        scriptRequest.systemPrompt() != null ? scriptRequest.systemPrompt().trim() : script.systemPrompt()))
                .speechEngine(new AiAgentSpeechEngine(
                        pipelineMode,
                        realtimeProvider,
                        engineRequest.pipecatStt() != null ? trimmed(engineRequest.pipecatStt()) : speechEngine.pipecatStt(),
                        engineRequest.pipecatLlm() != null ? trimmed(engineRequest.pipecatLlm()) : speechEngine.pipecatLlm(),
                        engineRequest.pipecatTts() != null ? trimmed(engineRequest.pipecatTts()) : speechEngine.pipecatTts(),
                        sttProvider,
                        requestedSttModel != null
                                ? engineGuard.requireKnownSttModel(sttProvider, requestedSttModel) : speechEngine.sttModel(),
                        ttsProvider,
                        engineRequest.ttsModel() != null
                                ? engineGuard.requireKnownTtsModel(ttsProvider, engineRequest.ttsModel()) : speechEngine.ttsModel()))
                .voice(new AiAgentVoice(
                        ttsVoice,
                        voiceRequest.speed() != null ? voiceRequest.speed() : voice.speed(),
                        voiceRequest.stability() != null ? voiceRequest.stability() : voice.stability(),
                        voiceRequest.similarityBoost() != null ? voiceRequest.similarityBoost() : voice.similarityBoost(),
                        languageVoices,
                        voiceRequest.perLanguageSpeed() != null ? voiceRequest.perLanguageSpeed() : voice.perLanguageSpeed(),
                        voiceRequest.emotionAdaptive() != null ? voiceRequest.emotionAdaptive() : voice.emotionAdaptive(),
                        voiceRequest.warmth() != null ? voiceRequest.warmth() : voice.warmth()))
                .ambience(AiAgentSettingsMapper.toAiAgentAmbience(request.ambience(), existing.ambience()))
                .callBehaviour(AiAgentSettingsMapper.toAiAgentCallBehaviour(request.callBehaviour(), existing.callBehaviour()))
                .limits(AiAgentSettingsMapper.toAiAgentLimits(request.limits(), existing.limits()))
                .dataPolicy(AiAgentSettingsMapper.toAiAgentDataPolicy(request.dataPolicy(), existing.dataPolicy()))
                .llmModel(llmModel)
                .fastLlmModel(fastLlmModel)
                .temperature(temperature != null ? temperature : existing.temperature())
                .maxOutputTokens(maxOutputTokens != null ? maxOutputTokens : existing.maxOutputTokens())
                .preemptiveGeneration(request.preemptiveGeneration() != null ? request.preemptiveGeneration() : existing.preemptiveGeneration())
                .ivrNavigationEnabled(request.ivrNavigationEnabled() != null ? request.ivrNavigationEnabled() : existing.ivrNavigationEnabled())
                .useRag(request.useRag() != null ? request.useRag() : existing.useRag())
                .dataNeeded(request.dataNeeded() != null ? request.dataNeeded() : existing.dataNeeded())
                .dataEvaluation(request.dataEvaluation() != null ? request.dataEvaluation() : existing.dataEvaluation())
                .initiationWebhook(request.initiationWebhook() != null ? request.initiationWebhook() : existing.initiationWebhook())
                .postCallWebhook(request.postCallWebhook() != null ? request.postCallWebhook() : existing.postCallWebhook())
                .sipTrunkIds(request.sipTrunkIds() != null ? request.sipTrunkIds() : existing.sipTrunkIds())
                .enabled(request.enabled() != null ? request.enabled() : existing.enabled())
                // Not sent: the version just read, so a client that predates it edits as before.
                .version(request.version() != null ? request.version() : existing.version())
                .build();

        AiAgentDrafts.requireCompatibleDataPolicy(updated.dataPolicy().zeroPiiRetention(), updated.dataPolicy().storeCallAudio());
        drafts.update(companyId, id, updated);
        auditUseCase.record(companyId, "AI_AGENT_UPDATE", "ai_agent", String.valueOf(id), updated.name());
        return findAgentRow(companyId, id);
    }

    @Override
    public AiAgentRow findAgentRow(long companyId, long id) {
        AiAgent agent = requireAgent(companyId, id);
        AiAgentVersion published = versionRepository.findPublished(companyId, id);
        return row(companyId, agent,
                published != null ? published.version() : null,
                published != null && !AiAgentSnapshot.isSameConfiguration(agent, published.snapshot()));
    }

    @Override
    public AiAgentRow findVersionRow(long companyId, long id, int version) {
        AiAgent draft = requireAgent(companyId, id);
        AiAgent snapshot = drafts.requireVersion(companyId, id, version).snapshot();
        return row(companyId, snapshot, version, !AiAgentSnapshot.isSameConfiguration(draft, snapshot));
    }

    @Override
    public PageableData<AiAgentRow> filterAgents(long companyId, AiAgentFilter filter) {
        List<AiAgent> agents = repository.findAll(companyId, filter);
        long total = repository.count(companyId, filter);
        Set<Long> scenarioIds = agents.stream()
                .map(agent -> agent.script().scenarioId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> scenarioNames = scenarioIds.isEmpty()
                ? Map.of()
                : scenarioUseCase.findNamesByIds(companyId, scenarioIds);
        Map<Long, String> creatorNames = userUseCase.findNamesByIds(companyId,
                agents.stream().map(AiAgent::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, AiAgentVersion> published = versionRepository.findPublishedByAgentIds(companyId,
                        agents.stream().map(AiAgent::id).toList()).stream()
                .collect(Collectors.toMap(AiAgentVersion::agentId, v -> v));
        List<EngineProfile> profiles = aiAgentCatalogUseCase.findEngineProfiles();
        List<AiAgentRow> rows = agents.stream()
                .map(agent -> {
                    AiAgentVersion live = published.get(agent.id());
                    return AiAgentRow.of(agent,
                            agent.script().scenarioId() != null ? scenarioNames.get(agent.script().scenarioId()) : null,
                            agent.createdBy() != null ? creatorNames.get(agent.createdBy()) : null,
                            findEngineProfileId(agent, profiles),
                            live != null ? live.version() : null,
                            live != null && !AiAgentSnapshot.isSameConfiguration(agent, live.snapshot()));
                })
                .toList();
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public void deleteAgent(long companyId, long id) {
        requireAgent(companyId, id);
        long campaigns = repository.countCampaignsUsing(id);
        long routes = repository.countInboundRoutesUsing(id);
        if (campaigns > 0 || routes > 0) {
            throw new ConflictException(ErrorCode.AI_AGENT_IN_USE, id, campaigns, routes);
        }
        repository.delete(companyId, id);
        auditUseCase.record(companyId, "AI_AGENT_DELETE", "ai_agent", String.valueOf(id), null);
    }

    @Override
    @Transactional
    public AiAgentRow cloneAgent(long companyId, long id) {
        AiAgent source = requireAgent(companyId, id);
        AiAgent copy = source.asCopy(source.name() + " (nusxa)", Instant.now(), currentUser.id().orElse(null));
        long copyId = repository.create(copy);
        // Tool links live in the tool feature, which copies them on this event inside the
        // same transaction; knowledge sources are not copied — their embeddings belong to
        // the source agent and are re-uploaded, not cloned.
        eventPublisher.publishEvent(new AiAgentClonedEvent(companyId, id, copyId));
        auditUseCase.record(companyId, "AI_AGENT_CLONE", "ai_agent", String.valueOf(copyId), "from " + id);
        return findAgentRow(companyId, copyId);
    }

    @Override
    public AiAgent requirePublishedAgent(long companyId, long id) {
        AiAgent draft = requireAgent(companyId, id);
        AiAgentVersion published = versionRepository.findPublished(companyId, id);
        // Never published: the draft is all there is, and calls keep running on it as before.
        return published == null ? draft : published.snapshot().withEnabled(draft.enabled());
    }

    @Override
    public AiAgent requireCallAgent(long companyId, long id, String phone) {
        AgentRollout rollout = rolloutRepository.findByAgentId(companyId, id);
        if (rollout != null && AgentRolloutRouter.routesToCandidate(rollout, PhoneNumbers.normalize(phone))) {
            AiAgentVersion candidate = versionRepository.findByAgentIdAndVersion(companyId, id, rollout.candidateVersion());
            if (candidate != null) {
                return candidate.snapshot().withEnabled(requireAgent(companyId, id).enabled());
            }
        }
        return requirePublishedAgent(companyId, id);
    }

    @Override
    public AiAgent requireAgentVersion(long companyId, long id, Integer version) {
        AiAgent draft = requireAgent(companyId, id);
        return version == null ? draft : drafts.requireVersion(companyId, id, version).snapshot().withEnabled(draft.enabled());
    }

    @Override
    public AiAgent requireAgent(long companyId, long id) {
        return drafts.requireAgent(companyId, id);
    }

    @Override
    public AiAgent findAgent(long companyId, long id) {
        return repository.findByCompanyIdAndId(companyId, id);
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        return repository.findNamesByIds(ids);
    }

    @Override
    public Scenario resolveScenario(long companyId, AiAgent agent) {
        if (agent == null) {
            return null;
        }
        if (agent.script().scenarioId() != null) {
            return scenarioUseCase.requireScenario(companyId, agent.script().scenarioId());
        }
        if (agent.script().definition() != null) {
            return new Scenario(
                    0L,
                    "agent-" + agent.id(),
                    1,
                    agent.name(),
                    agent.description(),
                    false,
                    true,
                    agent.script().definition(),
                    agent.createdAt() != null ? agent.createdAt() : Instant.now(),
                    null
            );
        }
        String rolePrompt = (agent.script().systemPrompt() != null && !agent.script().systemPrompt().isBlank())
                ? agent.script().systemPrompt()
                : ("Siz " + agent.name() + " sun'iy intellekt yordamchisiz. Mijoz bilan xushmuomala suhbatlashing.");
        StageDef defaultStage = StageDef.builder()
                .id("CONVERSATION")
                .purpose("Engage in natural conversation based on agent instructions")
                .allowedTransitions(List.of())
                .allowedTools(List.of())
                .build();
        ScenarioDefinition definition = ScenarioDefinition.builder()
                .stages(List.of(defaultStage))
                .factSchema(List.of())
                .tools(List.of())
                .outcomeSchema(List.of())
                .rolePrompt(rolePrompt)
                .guardrails(List.of())
                .build();
        return new Scenario(
                0L,
                "agent-" + agent.id(),
                1,
                agent.name(),
                agent.description(),
                false,
                true,
                definition,
                agent.createdAt() != null ? agent.createdAt() : Instant.now(),
                null
        );
    }

    @Override
    @Transactional
    public void changeScenarioGuardrail(long companyId, long agentId, String rule, boolean add) {
        String sentence = rule == null ? "" : rule.trim();
        if (sentence.isEmpty()) {
            throw new ValidationException(ErrorCode.AI_AGENT_GUARDRAIL_EMPTY);
        }
        AiAgent agent = drafts.requireAgent(companyId, agentId);
        AiAgentScript script = agent.script();
        if (script.scenarioId() != null) {
            // Shared with every other agent speaking it: a rule added here would change calls
            // nobody looking at this agent knows about.
            throw new ConflictException(ErrorCode.AI_AGENT_SCENARIO_SHARED, agentId, script.scenarioId());
        }
        AiAgentScript changed = script.definition() != null
                ? script.withDefinition(withGuardrail(script.definition(), sentence, add))
                : script.withSystemPrompt(withLine(script.systemPrompt(), sentence, add));
        drafts.update(companyId, agentId, agent.withScenario(changed));
        auditUseCase.record(companyId, add ? "AI_AGENT_GUARDRAIL_ADD" : "AI_AGENT_GUARDRAIL_REMOVE",
                "ai_agent", String.valueOf(agentId), sentence);
    }

    /** The same definition with one rule more, or one fewer; everything else untouched. */
    private static ScenarioDefinition withGuardrail(ScenarioDefinition definition, String rule, boolean add) {
        List<String> rules = new ArrayList<>(definition.guardrails() == null ? List.of() : definition.guardrails());
        if (add) {
            if (rules.contains(rule)) {
                return definition;
            }
            rules.add(rule);
        } else {
            rules.remove(rule);
        }
        return definition.toBuilder().guardrails(List.copyOf(rules)).build();
    }

    /** The same for an agent whose whole script is its role prompt: the rule is a line of it. */
    private static String withLine(String prompt, String rule, boolean add) {
        String text = prompt == null ? "" : prompt;
        if (!add) {
            return text.replace("\n" + rule, "").replace(rule, "").trim();
        }
        return text.contains(rule) ? text : (text.isBlank() ? rule : text.trim() + "\n" + rule);
    }

    private AiAgentRow row(long companyId, AiAgent agent, Integer publishedVersion, boolean hasDraftChanges) {
        String scenarioName = (agent.script().scenarioId() != null)
                ? scenarioUseCase.findNamesByIds(companyId, List.of(agent.script().scenarioId())).get(agent.script().scenarioId())
                : null;
        String createdByName = agent.createdBy() != null
                ? userUseCase.findNamesByIds(companyId, List.of(agent.createdBy())).get(agent.createdBy())
                : null;
        return AiAgentRow.of(agent, scenarioName, createdByName,
                findEngineProfileId(agent, aiAgentCatalogUseCase.findEngineProfiles()), publishedVersion, hasDraftChanges);
    }

    /** The offered profile whose engine choices the agent has exactly, or null when they were tuned by hand. */
    private static String findEngineProfileId(AiAgent agent, List<EngineProfile> profiles) {
        return profiles.stream()
                .filter(profile -> profile.isAppliedTo(agent))
                .map(EngineProfile::id)
                .findFirst()
                .orElse(null);
    }

    /** What the request names, else the profile's value, else null — the caller then keeps the agent's own. */
    private static <T> T chooseValue(T requested, EngineProfile profile, Function<EngineProfile, T> field) {
        if (requested != null) {
            return requested;
        }
        return profile != null ? field.apply(profile) : null;
    }

    /** A trimmed value, or null — a blank engine name means "the configured default". */
    private static String trimmed(String value) {
        return value != null ? value.trim() : null;
    }
}
