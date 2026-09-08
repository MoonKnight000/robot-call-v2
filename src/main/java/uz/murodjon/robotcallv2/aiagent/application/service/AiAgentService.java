package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.agent.realtime.RealtimeProviderRegistry;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.tts.TtsProviderSelector;
import uz.murodjon.robotcallv2.aiagent.application.dto.*;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AgentTemplate;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aiagent.domain.enums.ScenarioMode;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentTemplates;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentValidator;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;
import uz.murodjon.robotcallv2.user.application.service.UserService;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI agents — the voice, persona, model and speech engine a scenario is spoken with.
 */
@Service
public class AiAgentService implements AiAgentUseCase {

    /**
     * Sub-engines the pipecat build can be pointed at. Listed here rather than discovered
     * the way the cascade providers above them are: nothing in this process implements
     * them, so there is no registry to ask — the settings screen is offering names the
     * external pipecat service understands.
     */
    private static final List<String> PIPECAT_STT_OPTIONS = List.of(
            "deepgram", "gemini", "soniox", "speechmatics", "yandex", "whisper"
    );
    private static final List<String> PIPECAT_LLM_OPTIONS = List.of(
            "claude-3-5-haiku", "claude-3-5-sonnet", "gemini-3.5-flash-lite", "gemini-3.8-flash",
            "gpt-4o-mini", "groq-llama-3.3-70b"
    );
    private static final List<String> PIPECAT_TTS_OPTIONS = List.of(
            "cartesia", "elevenlabs", "gemini", "yandex", "google-chirp"
    );

    private final AiAgentRepository repository;
    private final ScenarioUseCase scenarioUseCase;
    private final TtsVoiceUseCase ttsVoiceUseCase;
    private final AiModelUseCase aiModelUseCase;
    private final CompanyConfigService companyConfigService;
    private final UserService userService;
    private final AuditService auditService;
    private final SttProviderSelector sttProviderSelector;
    private final TtsProviderSelector ttsProviderSelector;
    private final RealtimeProviderRegistry realtimeProviderRegistry;

    public AiAgentService(
            AiAgentRepository repository,
            ScenarioUseCase scenarioUseCase,
            TtsVoiceUseCase ttsVoiceUseCase,
            AiModelUseCase aiModelUseCase,
            CompanyConfigService companyConfigService,
            UserService userService,
            AuditService auditService,
            SttProviderSelector sttProviderSelector,
            TtsProviderSelector ttsProviderSelector,
            RealtimeProviderRegistry realtimeProviderRegistry
    ) {
        this.repository = repository;
        this.scenarioUseCase = scenarioUseCase;
        this.ttsVoiceUseCase = ttsVoiceUseCase;
        this.aiModelUseCase = aiModelUseCase;
        this.companyConfigService = companyConfigService;
        this.userService = userService;
        this.auditService = auditService;
        this.sttProviderSelector = sttProviderSelector;
        this.ttsProviderSelector = ttsProviderSelector;
        this.realtimeProviderRegistry = realtimeProviderRegistry;
    }

    @Override
    public AiAgentRow createAgent(long companyId, CreateAiAgentRequest request) {
        AiAgentValidator.validate(request.temperature(), request.maxOutputTokens());
        requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());

        // resolveLanguage already answers a blank request with the company's own default,
        // as its BCP-47 code. Deriving it here from the enum's name instead produced
        // "uz_uz", which is not a language code any recogniser or voice accepts.
        String language = companyConfigService.resolveLanguage(companyId, request.language());

        // Auto-populate from template presets if templateId is specified and prompts are omitted
        TemplateDefaults defaults = (request.templateId() != null)
                ? AiAgentTemplates.getPreset(request.templateId(), language)
                : null;

        String firstMessage = (request.firstMessage() != null && !request.firstMessage().isBlank())
                ? request.firstMessage().trim()
                : (defaults != null ? defaults.firstMessage() : null);

        String systemPrompt = (request.systemPrompt() != null && !request.systemPrompt().isBlank())
                ? request.systemPrompt().trim()
                : (defaults != null ? defaults.systemPrompt() : null);

        List<DataExtractionField> dataNeeded = (request.dataNeeded() != null && !request.dataNeeded().isEmpty())
                ? request.dataNeeded()
                : (defaults != null ? defaults.dataNeeded() : List.of());

        List<DataEvaluationCriterion> dataEvaluation = (request.dataEvaluation() != null && !request.dataEvaluation().isEmpty())
                ? request.dataEvaluation()
                : (defaults != null ? defaults.dataEvaluation() : List.of());

        // The voice and model families depend on which pipeline this agent will run, so the
        // mode has to be settled before anything is checked against it.
        PipelineMode pipelineMode = request.pipelineMode() != null ? request.pipelineMode() : PipelineMode.CASCADE;
        String realtimeProvider = trimmed(request.realtimeProvider());
        String ttsVoice = requireKnownVoice(pipelineMode, request.ttsVoice());
        String llmModel = requireKnownModel(pipelineMode, realtimeProvider, request.llmModel());
        String fastLlmModel = requireKnownModel(pipelineMode, realtimeProvider, request.fastLlmModel());
        Map<String, String> checkedLanguageVoices = requireKnownVoicePerLanguage(companyId, pipelineMode, request.languageVoices());

        if (request.scenarioId() != null) {
            scenarioUseCase.requireScenario(companyId, request.scenarioId());
        }

        // Everything the request left out is defaulted by the group it belongs to, so what
        // is written here is only what a create means beyond those defaults.
        Instant now = Instant.now();
        AiAgent agent = new AiAgent(
                0L,
                companyId,
                request.name().trim(),
                request.description() != null ? request.description().trim() : null,
                language,
                request.persona(),
                new AiAgentScript(
                        request.templateId(),
                        request.scenarioId(),
                        request.scenarioMode(),
                        request.scenarioDefinition(),
                        firstMessage,
                        systemPrompt),
                new AiAgentSpeechEngine(
                        pipelineMode,
                        realtimeProvider,
                        trimmed(request.pipecatStt()),
                        trimmed(request.pipecatLlm()),
                        trimmed(request.pipecatTts()),
                        trimmed(request.sttProvider()),
                        requireKnownSttModel(request.sttProvider(), request.sttModel()),
                        trimmed(request.ttsProvider()),
                        requireKnownTtsModel(request.ttsProvider(), request.ttsModel())),
                new AiAgentVoice(
                        ttsVoice,
                        request.voiceSpeed(),
                        request.voiceStability(),
                        request.voiceSimilarityBoost(),
                        checkedLanguageVoices,
                        request.emotionAdaptiveVoice() != null && request.emotionAdaptiveVoice()),
                new AiAgentAmbience(
                        request.ambientSound(),
                        request.ambientSoundVolume(),
                        request.ambientSoundFadeInSeconds(),
                        request.thinkingSound(),
                        request.thinkingSoundVolume(),
                        request.noiseCancellationEnabled() == null || request.noiseCancellationEnabled(),
                        request.noiseCancellationMode()),
                new AiAgentCallBehaviour(
                        request.interruptionSensitivity(),
                        request.endpointingDelayMs() != null ? request.endpointingDelayMs() : 0,
                        request.dtmfInputEnabled() == null || request.dtmfInputEnabled(),
                        request.voicemailAction(),
                        request.voicemailMessage(),
                        request.midCallSmsEnabled() != null && request.midCallSmsEnabled(),
                        request.midCallSmsTemplate(),
                        request.transferPhoneNumber(),
                        request.transferMessage()),
                new AiAgentLimits(
                        request.maxConversationDurationSeconds(),
                        request.silenceEndCallTimeoutSeconds(),
                        request.turnTimeoutSeconds(),
                        request.concurrentCallsLimit(),
                        request.dailyCallsLimit()),
                new AiAgentDataPolicy(
                        request.zeroPiiRetention() != null && request.zeroPiiRetention(),
                        request.storeCallAudio() == null || request.storeCallAudio(),
                        request.conversationRetentionDays()),
                llmModel,
                fastLlmModel,
                request.temperature(),
                request.maxOutputTokens(),
                request.preemptiveGeneration() != null && request.preemptiveGeneration(),
                request.ivrNavigationEnabled() == null || request.ivrNavigationEnabled(),
                request.useRag() != null && request.useRag(),
                dataNeeded,
                dataEvaluation,
                request.initiationWebhook(),
                request.postCallWebhook(),
                List.of(),
                List.of(),
                request.sipTrunkIds(),
                request.enabled() == null || request.enabled(),
                now,
                null
        );

        long id = repository.create(agent);
        auditService.record(companyId, "AI_AGENT_CREATE", "ai_agent", String.valueOf(id), agent.name());
        return findAgentRow(companyId, id);
    }

    @Override
    public AiAgentRow updateAgent(long companyId, long id, UpdateAiAgentRequest request) {
        AiAgent existing = requireAgent(companyId, id);
        AiAgentValidator.validate(request.temperature(), request.maxOutputTokens());
        requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());

        String language = (request.language() != null && !request.language().isBlank())
                ? companyConfigService.resolveLanguage(companyId, request.language())
                : existing.language();

        // Checked against the mode the agent will have after this edit, not the one it had:
        // switching to REALTIME and picking a realtime voice arrives as a single request.
        PipelineMode pipelineMode = request.pipelineMode() != null
                ? request.pipelineMode()
                : existing.speechEngine().mode();

        String ttsVoice = (request.ttsVoice() != null)
                ? requireKnownVoice(pipelineMode, request.ttsVoice())
                : existing.voice().defaultVoice();

        // Same reason the mode is settled first: switching engine and model arrives as one
        // request, and the model has to be checked against the engine it will run on.
        String realtimeProvider = request.realtimeProvider() != null
                ? trimmed(request.realtimeProvider())
                : existing.speechEngine().realtimeProvider();

        String llmModel = (request.llmModel() != null)
                ? requireKnownModel(pipelineMode, realtimeProvider, request.llmModel())
                : existing.llmModel();

        String fastLlmModel = (request.fastLlmModel() != null)
                ? requireKnownModel(pipelineMode, realtimeProvider, request.fastLlmModel())
                : existing.fastLlmModel();

        Map<String, String> languageVoices = (request.languageVoices() != null)
                ? requireKnownVoicePerLanguage(companyId, pipelineMode, request.languageVoices())
                : existing.voice().perLanguage();

        Long scenarioId = existing.script().scenarioId();
        if (request.scenarioId() != null) {
            scenarioUseCase.requireScenario(companyId, request.scenarioId());
            scenarioId = request.scenarioId();
        }

        AiAgentScript script = existing.script();
        AiAgentSpeechEngine speechEngine = existing.speechEngine();
        // The model is checked against the provider it will actually run on, which is the
        // one this request leaves behind — changing only the model must not be validated
        // against a provider the agent no longer has, nor the other way round.
        String sttProvider = request.sttProvider() != null ? trimmed(request.sttProvider()) : speechEngine.sttProvider();
        String ttsProvider = request.ttsProvider() != null ? trimmed(request.ttsProvider()) : speechEngine.ttsProvider();
        AiAgentVoice voice = existing.voice();
        AiAgentAmbience ambience = existing.ambience();
        AiAgentCallBehaviour behaviour = existing.callBehaviour();
        AiAgentLimits limits = existing.limits();
        AiAgentDataPolicy dataPolicy = existing.dataPolicy();

        AiAgent updated = new AiAgent(
                id,
                companyId,
                request.name() != null ? request.name().trim() : existing.name(),
                request.description() != null ? request.description().trim() : existing.description(),
                language,
                request.persona() != null ? request.persona() : existing.persona(),
                new AiAgentScript(
                        request.templateId() != null ? request.templateId() : script.templateId(),
                        scenarioId,
                        request.scenarioMode() != null ? request.scenarioMode() : script.mode(),
                        request.scenarioDefinition() != null ? request.scenarioDefinition() : script.definition(),
                        request.firstMessage() != null ? request.firstMessage().trim() : script.firstMessage(),
                        request.systemPrompt() != null ? request.systemPrompt().trim() : script.systemPrompt()),
                new AiAgentSpeechEngine(
                        pipelineMode,
                        realtimeProvider,
                        request.pipecatStt() != null ? trimmed(request.pipecatStt()) : speechEngine.pipecatStt(),
                        request.pipecatLlm() != null ? trimmed(request.pipecatLlm()) : speechEngine.pipecatLlm(),
                        request.pipecatTts() != null ? trimmed(request.pipecatTts()) : speechEngine.pipecatTts(),
                        sttProvider,
                        request.sttModel() != null ? requireKnownSttModel(sttProvider, request.sttModel()) : speechEngine.sttModel(),
                        ttsProvider,
                        request.ttsModel() != null ? requireKnownTtsModel(ttsProvider, request.ttsModel()) : speechEngine.ttsModel()),
                new AiAgentVoice(
                        ttsVoice,
                        request.voiceSpeed() != null ? request.voiceSpeed() : voice.speed(),
                        request.voiceStability() != null ? request.voiceStability() : voice.stability(),
                        request.voiceSimilarityBoost() != null ? request.voiceSimilarityBoost() : voice.similarityBoost(),
                        languageVoices,
                        request.emotionAdaptiveVoice() != null ? request.emotionAdaptiveVoice() : voice.emotionAdaptive()),
                new AiAgentAmbience(
                        request.ambientSound() != null ? request.ambientSound() : ambience.background(),
                        request.ambientSoundVolume() != null ? request.ambientSoundVolume() : ambience.backgroundVolume(),
                        request.ambientSoundFadeInSeconds() != null ? request.ambientSoundFadeInSeconds() : ambience.backgroundFadeInSeconds(),
                        request.thinkingSound() != null ? request.thinkingSound() : ambience.thinking(),
                        request.thinkingSoundVolume() != null ? request.thinkingSoundVolume() : ambience.thinkingVolume(),
                        request.noiseCancellationEnabled() != null ? request.noiseCancellationEnabled() : ambience.noiseCancellationEnabled(),
                        request.noiseCancellationMode() != null ? request.noiseCancellationMode() : ambience.noiseCancellationMode()),
                new AiAgentCallBehaviour(
                        request.interruptionSensitivity() != null ? request.interruptionSensitivity() : behaviour.interruptionSensitivity(),
                        request.endpointingDelayMs() != null ? request.endpointingDelayMs() : behaviour.endpointingDelayMs(),
                        request.dtmfInputEnabled() != null ? request.dtmfInputEnabled() : behaviour.dtmfInputEnabled(),
                        request.voicemailAction() != null ? request.voicemailAction() : behaviour.voicemailAction(),
                        request.voicemailMessage() != null ? request.voicemailMessage() : behaviour.voicemailMessage(),
                        request.midCallSmsEnabled() != null ? request.midCallSmsEnabled() : behaviour.midCallSmsEnabled(),
                        request.midCallSmsTemplate() != null ? request.midCallSmsTemplate() : behaviour.midCallSmsTemplate(),
                        request.transferPhoneNumber() != null ? request.transferPhoneNumber() : behaviour.transferPhoneNumber(),
                        request.transferMessage() != null ? request.transferMessage() : behaviour.transferMessage()),
                new AiAgentLimits(
                        request.maxConversationDurationSeconds() != null ? request.maxConversationDurationSeconds() : limits.maxConversationDurationSeconds(),
                        request.silenceEndCallTimeoutSeconds() != null ? request.silenceEndCallTimeoutSeconds() : limits.silenceEndCallTimeoutSeconds(),
                        request.turnTimeoutSeconds() != null ? request.turnTimeoutSeconds() : limits.turnTimeoutSeconds(),
                        request.concurrentCallsLimit() != null ? request.concurrentCallsLimit() : limits.concurrentCallsLimit(),
                        request.dailyCallsLimit() != null ? request.dailyCallsLimit() : limits.dailyCallsLimit()),
                new AiAgentDataPolicy(
                        request.zeroPiiRetention() != null ? request.zeroPiiRetention() : dataPolicy.zeroPiiRetention(),
                        request.storeCallAudio() != null ? request.storeCallAudio() : dataPolicy.storeCallAudio(),
                        request.conversationRetentionDays() != null ? request.conversationRetentionDays() : dataPolicy.conversationRetentionDays()),
                llmModel,
                fastLlmModel,
                request.temperature() != null ? request.temperature() : existing.temperature(),
                request.maxOutputTokens() != null ? request.maxOutputTokens() : existing.maxOutputTokens(),
                request.preemptiveGeneration() != null ? request.preemptiveGeneration() : existing.preemptiveGeneration(),
                request.ivrNavigationEnabled() != null ? request.ivrNavigationEnabled() : existing.ivrNavigationEnabled(),
                request.useRag() != null ? request.useRag() : existing.useRag(),
                request.dataNeeded() != null ? request.dataNeeded() : existing.dataNeeded(),
                request.dataEvaluation() != null ? request.dataEvaluation() : existing.dataEvaluation(),
                request.initiationWebhook() != null ? request.initiationWebhook() : existing.initiationWebhook(),
                request.postCallWebhook() != null ? request.postCallWebhook() : existing.postCallWebhook(),
                existing.pronunciationRules(),
                existing.postCallActions(),
                request.sipTrunkIds() != null ? request.sipTrunkIds() : existing.sipTrunkIds(),
                request.enabled() != null ? request.enabled() : existing.enabled(),
                existing.createdAt(),
                existing.createdBy()
        );

        repository.update(companyId, id, updated);
        auditService.record(companyId, "AI_AGENT_UPDATE", "ai_agent", String.valueOf(id), updated.name());
        return findAgentRow(companyId, id);
    }

    @Override
    public AiAgentRow findAgentRow(long companyId, long id) {
        AiAgent agent = requireAgent(companyId, id);
        String scenarioName = (agent.script().scenarioId() != null)
                ? scenarioUseCase.scenarioNamesByIds(companyId, List.of(agent.script().scenarioId())).get(agent.script().scenarioId())
                : null;
        String createdByName = agent.createdBy() != null
                ? userService.namesByIds(companyId, List.of(agent.createdBy())).get(agent.createdBy())
                : null;
        return AiAgentRow.of(agent, scenarioName, createdByName);
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
                : scenarioUseCase.scenarioNamesByIds(companyId, scenarioIds);
        Map<Long, String> creatorNames = userService.namesByIds(companyId,
                agents.stream().map(AiAgent::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<AiAgentRow> rows = agents.stream()
                .map(agent -> AiAgentRow.of(agent,
                        agent.script().scenarioId() != null ? scenarioNames.get(agent.script().scenarioId()) : null,
                        agent.createdBy() != null ? creatorNames.get(agent.createdBy()) : null))
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
        auditService.record(companyId, "AI_AGENT_DELETE", "ai_agent", String.valueOf(id), null);
    }

    @Override
    public AiAgent requireAgent(long companyId, long id) {
        AiAgent agent = repository.findByCompanyIdAndId(companyId, id);
        if (agent == null) {
            throw new NotFoundException(ErrorCode.AI_AGENT_NOT_FOUND, id);
        }
        return agent;
    }

    @Override
    public AiAgent findAgent(long companyId, long id) {
        return repository.findByCompanyIdAndId(companyId, id);
    }

    @Override
    public EngineOptionsResponse findEngineOptions() {
        return new EngineOptionsResponse(
                sttProviderSelector.names(),
                ttsProviderSelector.names(),
                realtimeProviderRegistry.names(),
                PIPECAT_STT_OPTIONS,
                PIPECAT_LLM_OPTIONS,
                PIPECAT_TTS_OPTIONS
        );
    }

    @Override
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        return repository.findNamesByIds(ids);
    }

    @Override
    public List<TemplatePresetDto> getTemplates(String language) {
        return List.of(
                new TemplatePresetDto(AgentTemplate.BUSINESS, "Business Agent",
                        "General purpose business calls, lead qualification, and customer questions",
                        AiAgentTemplates.getPreset(AgentTemplate.BUSINESS, language)),
                new TemplatePresetDto(AgentTemplate.SUPPORT, "Support Agent",
                        "Customer support, ticket triage, and issue troubleshooting",
                        AiAgentTemplates.getPreset(AgentTemplate.SUPPORT, language)),
                new TemplatePresetDto(AgentTemplate.MEDICAL, "Medical Agent",
                        "Patient intake, appointment scheduling, and clinic front-desk workflows",
                        AiAgentTemplates.getPreset(AgentTemplate.MEDICAL, language)),
                new TemplatePresetDto(AgentTemplate.BLANK, "Blank Agent",
                        "Empty configuration to design your own custom agent behavior",
                        AiAgentTemplates.getPreset(AgentTemplate.BLANK, language))
        );
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
        StageDef defaultStage = new StageDef("CONVERSATION", "Engage in natural conversation based on agent instructions", List.of(), List.of());
        ScenarioDefinition definition = new ScenarioDefinition(
                List.of(defaultStage),
                List.of(),
                List.of(),
                List.of(),
                rolePrompt,
                List.of(),
                null,
                null
        );
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
    public AgentScenarioDto getScenario(long companyId, long agentId) {
        AiAgent agent = requireAgent(companyId, agentId);
        ScenarioDefinition def = agent.script().definition();
        return new AgentScenarioDto(
                agent.script().mode(),
                agent.script().firstMessage(),
                agent.script().systemPrompt(),
                def != null ? def.stages() : List.of(),
                def != null ? def.guardrails() : List.of(),
                def != null ? def.factSchema() : List.of(),
                def != null ? def.disclosureText() : null
        );
    }

    @Override
    @Transactional
    public AgentScenarioDto updateScenario(long companyId, long agentId, AgentScenarioDto request) {
        AiAgent existing = requireAgent(companyId, agentId);
        String rolePrompt = request.systemPrompt() != null ? request.systemPrompt().trim() : existing.script().systemPrompt();
        String firstMessage = request.firstMessage() != null ? request.firstMessage().trim() : existing.script().firstMessage();
        ScenarioMode mode = request.scenarioModeOrDefault();

        List<StageDef> stages = request.stagesOrEmpty();
        if (stages.isEmpty()) {
            stages = List.of(new StageDef("CONVERSATION", "Engage in natural conversation based on agent instructions", List.of(), List.of()));
        }

        ScenarioDefinition def = new ScenarioDefinition(
                stages,
                request.factSchemaOrEmpty(),
                List.of(),
                List.of(),
                rolePrompt != null ? rolePrompt : "",
                request.guardrailsOrEmpty(),
                request.disclosureText(),
                null
        );

        AiAgent updated = existing.withScenario(new AiAgentScript(
                existing.script().templateId(), existing.script().scenarioId(), mode, def, firstMessage, rolePrompt));
        repository.update(companyId, agentId, updated);
        auditService.record(companyId, "AI_AGENT_SCENARIO_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return getScenario(companyId, agentId);
    }

    @Override
    public AgentAnalysisDto getAnalysis(long companyId, long agentId) {
        AiAgent agent = requireAgent(companyId, agentId);
        return new AgentAnalysisDto(agent.dataNeeded(), agent.dataEvaluation());
    }

    @Override
    @Transactional
    public AgentAnalysisDto updateAnalysis(long companyId, long agentId, AgentAnalysisDto request) {
        AiAgent existing = requireAgent(companyId, agentId);
        AiAgent updated = existing.withAnalysis(
                request.dataNeeded() != null ? request.dataNeeded() : existing.dataNeeded(),
                request.dataEvaluation() != null ? request.dataEvaluation() : existing.dataEvaluation()
        );
        repository.update(companyId, agentId, updated);
        auditService.record(companyId, "AI_AGENT_ANALYSIS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentAnalysisDto(updated.dataNeeded(), updated.dataEvaluation());
    }

    @Override
    public AgentLimitsDto getLimits(long companyId, long agentId) {
        AiAgent agent = requireAgent(companyId, agentId);
        return new AgentLimitsDto(
                agent.limits().concurrentCallsLimit(),
                agent.limits().dailyCallsLimit(),
                agent.limits().maxConversationDurationSeconds(),
                agent.limits().silenceEndCallTimeoutSeconds(),
                agent.limits().turnTimeoutSeconds()
        );
    }

    @Override
    @Transactional
    public AgentLimitsDto updateLimits(long companyId, long agentId, AgentLimitsDto request) {
        AiAgent existing = requireAgent(companyId, agentId);
        AiAgentLimits limits = existing.limits();
        AiAgent updated = existing.withLimits(new AiAgentLimits(
                request.maxConversationDurationSeconds() != null ? request.maxConversationDurationSeconds() : limits.maxConversationDurationSeconds(),
                request.silenceEndCallTimeoutSeconds() != null ? request.silenceEndCallTimeoutSeconds() : limits.silenceEndCallTimeoutSeconds(),
                request.turnTimeoutSeconds() != null ? request.turnTimeoutSeconds() : limits.turnTimeoutSeconds(),
                request.concurrentCallsLimit() != null ? request.concurrentCallsLimit() : limits.concurrentCallsLimit(),
                request.dailyCallsLimit() != null ? request.dailyCallsLimit() : limits.dailyCallsLimit()
        ));
        repository.update(companyId, agentId, updated);
        auditService.record(companyId, "AI_AGENT_LIMITS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentLimitsDto(
                updated.limits().concurrentCallsLimit(),
                updated.limits().dailyCallsLimit(),
                updated.limits().maxConversationDurationSeconds(),
                updated.limits().silenceEndCallTimeoutSeconds(),
                updated.limits().turnTimeoutSeconds()
        );
    }

    @Override
    public AgentAdvancedDto getAdvanced(long companyId, long agentId) {
        AiAgent agent = requireAgent(companyId, agentId);
        return new AgentAdvancedDto(
                agent.useRag(),
                agent.preemptiveGeneration(),
                agent.ivrNavigationEnabled(),
                agent.dataPolicy().storeCallAudio(),
                agent.dataPolicy().zeroPiiRetention(),
                agent.dataPolicy().conversationRetentionDays(),
                agent.initiationWebhook(),
                agent.postCallWebhook(),
                agent.ambience().background(),
                agent.ambience().backgroundVolume(),
                agent.ambience().backgroundFadeInSeconds(),
                agent.ambience().thinking(),
                agent.ambience().thinkingVolume(),
                agent.ambience().noiseCancellationEnabled(),
                agent.ambience().noiseCancellationMode(),
                agent.voice().emotionAdaptive(),
                agent.callBehaviour().dtmfInputEnabled(),
                agent.callBehaviour().voicemailAction(),
                agent.callBehaviour().voicemailMessage(),
                agent.callBehaviour().midCallSmsEnabled(),
                agent.callBehaviour().midCallSmsTemplate(),
                agent.callBehaviour().transferPhoneNumber(),
                agent.callBehaviour().transferMessage(),
                agent.callBehaviour().interruptionSensitivity(),
                agent.callBehaviour().endpointingDelayMs()
        );
    }

    @Override
    @Transactional
    public AgentAdvancedDto updateAdvanced(long companyId, long agentId, AgentAdvancedDto request) {
        AiAgent existing = requireAgent(companyId, agentId);
        requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        boolean zeroPii = request.zeroPiiRetention() != null ? request.zeroPiiRetention() : existing.dataPolicy().zeroPiiRetention();
        boolean storeAudio = request.storeCallAudio() != null ? request.storeCallAudio() : existing.dataPolicy().storeCallAudio();
        if (zeroPii && storeAudio) {
            throw new ValidationException(ErrorCode.ZERO_PII_STORE_AUDIO_CONFLICT);
        }

        AiAgentAmbience ambience = existing.ambience();
        AiAgentCallBehaviour behaviour = existing.callBehaviour();
        AiAgentVoice voice = existing.voice();

        AiAgent updated = existing.withAdvanced(
                new AiAgentAmbience(
                        request.ambientSound() != null ? request.ambientSound() : ambience.background(),
                        request.ambientSoundVolume() != null ? request.ambientSoundVolume() : ambience.backgroundVolume(),
                        request.ambientSoundFadeInSeconds() != null ? request.ambientSoundFadeInSeconds() : ambience.backgroundFadeInSeconds(),
                        request.thinkingSound() != null ? request.thinkingSound() : ambience.thinking(),
                        request.thinkingSoundVolume() != null ? request.thinkingSoundVolume() : ambience.thinkingVolume(),
                        request.noiseCancellationEnabled() != null ? request.noiseCancellationEnabled() : ambience.noiseCancellationEnabled(),
                        request.noiseCancellationMode() != null ? request.noiseCancellationMode() : ambience.noiseCancellationMode()),
                new AiAgentCallBehaviour(
                        request.interruptionSensitivity() != null ? request.interruptionSensitivity() : behaviour.interruptionSensitivity(),
                        request.endpointingDelayMs() != null ? request.endpointingDelayMs() : behaviour.endpointingDelayMs(),
                        request.dtmfInputEnabled() != null ? request.dtmfInputEnabled() : behaviour.dtmfInputEnabled(),
                        request.voicemailAction() != null ? request.voicemailAction() : behaviour.voicemailAction(),
                        request.voicemailMessage() != null ? request.voicemailMessage() : behaviour.voicemailMessage(),
                        request.midCallSmsEnabled() != null ? request.midCallSmsEnabled() : behaviour.midCallSmsEnabled(),
                        request.midCallSmsTemplate() != null ? request.midCallSmsTemplate() : behaviour.midCallSmsTemplate(),
                        request.transferPhoneNumber() != null ? request.transferPhoneNumber() : behaviour.transferPhoneNumber(),
                        request.transferMessage() != null ? request.transferMessage() : behaviour.transferMessage()),
                new AiAgentDataPolicy(
                        zeroPii,
                        storeAudio,
                        request.conversationRetentionDays() != null ? request.conversationRetentionDays() : existing.dataPolicy().conversationRetentionDays()),
                voice.withEmotionAdaptive(
                        request.emotionAdaptiveVoice() != null ? request.emotionAdaptiveVoice() : voice.emotionAdaptive()),
                request.preemptiveGeneration() != null ? request.preemptiveGeneration() : existing.preemptiveGeneration(),
                request.ivrNavigationEnabled() != null ? request.ivrNavigationEnabled() : existing.ivrNavigationEnabled(),
                request.useRag() != null ? request.useRag() : existing.useRag(),
                request.initiationWebhook() != null ? request.initiationWebhook() : existing.initiationWebhook(),
                request.postCallWebhook() != null ? request.postCallWebhook() : existing.postCallWebhook()
        );
        repository.update(companyId, agentId, updated);
        auditService.record(companyId, "AI_AGENT_ADVANCED_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return getAdvanced(companyId, agentId);
    }

    /**
     * Rejects a webhook aimed at this network while its author is still looking at the
     * form, rather than only when the call it belongs to has already happened.
     *
     * <p>An address holding a {@code {{secrets.KEY}}} placeholder is not a URL yet and is
     * left alone; what it resolves to is screened by {@code AgentWebhookExecutor} on every
     * delivery, which is where the real defence is.
     */
    private static void requireCallableWebhookUrls(AgentWebhookConfig... configs) {
        for (AgentWebhookConfig config : configs) {
            if (config == null || config.webhookUrl() == null || config.webhookUrl().isBlank()
                    || config.webhookUrl().indexOf('{') >= 0) {
                continue;
            }
            if (PublicUrlGuard.parsePublic(config.webhookUrl()) == null) {
                throw new ValidationException(ErrorCode.WEBHOOK_URL_INVALID, config.webhookUrl());
            }
        }
    }

    /** A trimmed value, or null — a blank engine name means "the configured default". */
    private static String trimmed(String value) {
        return value != null ? value.trim() : null;
    }

    private Map<String, String> requireKnownVoicePerLanguage(long companyId, PipelineMode pipelineMode,
                                                             Map<String, String> languageVoices) {
        if (languageVoices == null || languageVoices.isEmpty()) {
            return Map.of();
        }
        Map<String, String> checked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : languageVoices.entrySet()) {
            String language = companyConfigService.resolveLanguage(companyId, entry.getKey());
            String voiceId = requireKnownVoice(pipelineMode, entry.getValue());
            if (voiceId == null) {
                continue;
            }
            TtsVoice voice = ttsVoiceUseCase.find(voiceId);
            if (voice != null && !language.equalsIgnoreCase(voice.language())) {
                throw new ValidationException(ErrorCode.TTS_VOICE_LANGUAGE_MISMATCH,
                        voiceId, voice.language(), language);
            }
            checked.put(language, voiceId);
        }
        return checked;
    }

    private String requireKnownVoice(PipelineMode pipelineMode, String ttsVoice) {
        if (ttsVoice == null || ttsVoice.isBlank()) {
            return null;
        }
        String trimmed = ttsVoice.trim();
        if (!ttsVoiceUseCase.isSelectable(pipelineMode, trimmed)) {
            throw new ValidationException(ErrorCode.TTS_VOICE_UNKNOWN, trimmed,
                    ttsVoiceUseCase.findSelectableIds(pipelineMode));
        }
        return trimmed;
    }

    /**
     * The model this agent runs its turns on, or null for default.
     *
     * <p>On REALTIME the model belongs to one engine, the way an STT model belongs to one
     * recognizer: the id is handed to whichever engine the agent opens
     * ({@code RealtimeCallConfig#modelOr}), so {@code gpt-realtime-2.1} saved on a
     * Gemini Live agent is a call that cannot start. The pipeline check alone does not
     * catch it — both ids are REALTIME rows — so the pair is checked here, and only for
     * REALTIME: a cascade model has no engine to belong to.
     */
    private String requireKnownModel(PipelineMode pipelineMode, String realtimeProvider, String llmModel) {
        if (llmModel == null || llmModel.isBlank()) {
            return null;
        }
        String trimmed = llmModel.trim();
        if (!aiModelUseCase.isSelectable(AiModelKind.LLM, pipelineMode, trimmed)) {
            throw new ValidationException(ErrorCode.AI_MODEL_UNKNOWN, trimmed,
                    aiModelUseCase.findSelectableIds(AiModelKind.LLM, pipelineMode));
        }
        if (pipelineMode == PipelineMode.REALTIME && realtimeProvider != null && !realtimeProvider.isBlank()) {
            AiModel catalogued = aiModelUseCase.find(trimmed);
            if (catalogued != null && !catalogued.provider().equalsIgnoreCase(realtimeProvider.trim())) {
                throw new ValidationException(ErrorCode.REALTIME_MODEL_PROVIDER_MISMATCH,
                        trimmed, catalogued.provider());
            }
        }
        return trimmed;
    }

    /**
     * The recognition model this agent listens with, or null for its provider's own.
     *
     * <p>Checked against the same catalog rows the form was filled from, for the reason
     * {@code llm_model} is: the id is passed to the vendor verbatim, and one it does not
     * know is a call that hears nothing rather than a call that recognizes worse.
     */
    private String requireKnownSttModel(String sttProvider, String sttModel) {
        return requireKnownSpeechModel(AiModelKind.STT, sttProvider, sttModel,
                ErrorCode.STT_MODEL_UNKNOWN, ErrorCode.STT_MODEL_PROVIDER_MISMATCH);
    }

    /** The synthesis model this agent speaks with, or null for its provider's own. */
    private String requireKnownTtsModel(String ttsProvider, String ttsModel) {
        return requireKnownSpeechModel(AiModelKind.TTS, ttsProvider, ttsModel,
                ErrorCode.TTS_MODEL_UNKNOWN, ErrorCode.TTS_MODEL_PROVIDER_MISMATCH);
    }

    /**
     * A speech model is only accepted together with the provider that owns it. The two
     * fields are saved side by side and read side by side at call time, and a pair that
     * does not match — Deepgram's {@code nova-3} on an agent listening through Yandex —
     * reaches the vendor as an id it has never heard of, which costs the call its
     * recognition rather than degrading it.
     *
     * <p>The owner is looked up in the whole catalog, not among the ids this build can
     * reach: a model whose provider has no API key here is missing from the reachable list,
     * and reporting it as unknown sent the operator looking for a model that does exist
     * instead of at the provider field they had to change.
     */
    private String requireKnownSpeechModel(AiModelKind kind, String provider, String model,
                                           ErrorCode unknown, ErrorCode mismatch) {
        if (model == null || model.isBlank()) {
            return null;
        }
        String trimmed = model.trim();
        AiModel catalogued = aiModelUseCase.find(trimmed);
        if (catalogued != null && catalogued.kind() == kind
                && (provider == null || !catalogued.provider().equalsIgnoreCase(provider.trim()))) {
            throw new ValidationException(mismatch, trimmed, catalogued.provider());
        }
        List<AiModel> selectable = aiModelUseCase.findSelectableByKindAndMode(kind, null);
        if (selectable.stream().noneMatch(candidate -> candidate.id().equalsIgnoreCase(trimmed))) {
            throw new ValidationException(unknown, trimmed,
                    selectable.stream().map(AiModel::id).toList());
        }
        return trimmed;
    }

    @Override
    public AgentPronunciationDto getPronunciation(long companyId, long agentId) {
        AiAgent agent = requireAgent(companyId, agentId);
        return new AgentPronunciationDto(agent.pronunciationRules());
    }

    @Override
    @Transactional
    public AgentPronunciationDto updatePronunciation(long companyId, long agentId, AgentPronunciationDto request) {
        AiAgent existing = requireAgent(companyId, agentId);
        AiAgent updated = existing.withPronunciationRules(request.rulesOrEmpty());
        repository.update(companyId, agentId, updated);
        auditService.record(companyId, "AI_AGENT_PRONUNCIATION_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentPronunciationDto(updated.pronunciationRules());
    }

    @Override
    public AgentPostCallActionsDto getPostCallActions(long companyId, long agentId) {
        AiAgent agent = requireAgent(companyId, agentId);
        return new AgentPostCallActionsDto(agent.postCallActions());
    }

    @Override
    @Transactional
    public AgentPostCallActionsDto updatePostCallActions(long companyId, long agentId, AgentPostCallActionsDto request) {
        AiAgent existing = requireAgent(companyId, agentId);
        AiAgent updated = existing.withPostCallActions(request.actionsOrEmpty());
        repository.update(companyId, agentId, updated);
        auditService.record(companyId, "AI_AGENT_POST_CALL_ACTIONS_UPDATE", "ai_agent", String.valueOf(agentId), updated.name());
        return new AgentPostCallActionsDto(updated.postCallActions());
    }
}
