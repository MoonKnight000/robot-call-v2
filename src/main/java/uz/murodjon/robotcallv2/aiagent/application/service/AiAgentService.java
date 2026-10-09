package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
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
import java.util.stream.Collectors;

/**
 * AI agents — the voice, persona, model and speech engine a scenario is spoken with. This
 * service owns the agent's rows and lookups; its versions, section settings and catalog
 * live in {@link AiAgentVersionService}, {@link AiAgentSettingsService} and
 * {@link AiAgentCatalogService}.
 */
@Service
public class AiAgentService implements AiAgentUseCase {

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
            AgentRolloutRepository rolloutRepository
    ) {
        this.rolloutRepository = rolloutRepository;
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
        AiAgentDrafts.requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        drafts.requireOwnQueue(companyId, request.transferQueueId());
        drafts.requirePersonaAllowed(companyId, request.persona());

        // resolveLanguage already answers a blank request with the company's own default,
        // as its BCP-47 code. Deriving it here from the enum's name instead produced
        // "uz_uz", which is not a language code any recogniser or voice accepts.
        String language = companyConfigUseCase.resolveLanguage(companyId, request.language());

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
        String realtimeProvider = engineGuard.requireKnownRealtimeProvider(pipelineMode, request.realtimeProvider());
        String ttsVoice = engineGuard.requireKnownVoice(pipelineMode, request.ttsVoice());
        String llmModel = engineGuard.requireKnownModel(pipelineMode, realtimeProvider, request.llmModel());
        String fastLlmModel = engineGuard.requireKnownModel(pipelineMode, realtimeProvider, request.fastLlmModel());
        Map<String, String> checkedLanguageVoices =
                engineGuard.requireKnownVoicePerLanguage(companyId, pipelineMode, request.languageVoices());

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
                        engineGuard.requireKnownSttProvider(request.sttProvider()),
                        engineGuard.requireKnownSttModel(request.sttProvider(), request.sttModel()),
                        engineGuard.requireKnownTtsProvider(request.ttsProvider()),
                        engineGuard.requireKnownTtsModel(request.ttsProvider(), request.ttsModel())),
                new AiAgentVoice(
                        ttsVoice,
                        request.voiceSpeed(),
                        request.voiceStability(),
                        request.voiceSimilarityBoost(),
                        checkedLanguageVoices,
                        request.languageVoiceSpeeds(),
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
                        request.transferMessage(),
                        request.transferQueueId(),
                        request.preConversationAudioFileId()),
                new AiAgentLimits(
                        request.maxConversationDurationSeconds(),
                        request.silenceEndCallTimeoutSeconds(),
                        request.turnTimeoutSeconds(),
                        request.concurrentCallsLimit(),
                        request.dailyCallsLimit()),
                new AiAgentDataPolicy(
                        request.zeroPiiRetention() != null && request.zeroPiiRetention(),
                        request.storeCallAudio() == null || request.storeCallAudio(),
                        request.conversationRetentionDays(),
                        request.realtimeFactsDelivery()),
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
                null,
                null,
                0L
        );

        AiAgentDrafts.requireCompatibleDataPolicy(agent.dataPolicy().zeroPiiRetention(), agent.dataPolicy().storeCallAudio());
        long id = repository.create(drafts.validate(companyId, agent));
        auditUseCase.record(companyId, "AI_AGENT_CREATE", "ai_agent", String.valueOf(id), agent.name());
        return findAgentRow(companyId, id);
    }

    @Override
    public AiAgentRow updateAgent(long companyId, long id, UpdateAiAgentRequest request) {
        AiAgent existing = requireAgent(companyId, id);
        AiAgentDrafts.requireCallableWebhookUrls(request.initiationWebhook(), request.postCallWebhook());
        drafts.requireOwnQueue(companyId, request.transferQueueId());
        drafts.requirePersonaAllowed(companyId, request.persona());

        String language = (request.language() != null && !request.language().isBlank())
                ? companyConfigUseCase.resolveLanguage(companyId, request.language())
                : existing.language();

        // Checked against the mode the agent will have after this edit, not the one it had:
        // switching to REALTIME and picking a realtime voice arrives as a single request.
        PipelineMode pipelineMode = request.pipelineMode() != null
                ? request.pipelineMode()
                : existing.speechEngine().mode();

        String ttsVoice = (request.ttsVoice() != null)
                ? engineGuard.requireKnownVoice(pipelineMode, request.ttsVoice())
                : existing.voice().defaultVoice();

        // Same reason the mode is settled first: switching engine and model arrives as one
        // request, and the model has to be checked against the engine it will run on.
        String realtimeProvider = request.realtimeProvider() != null || request.pipelineMode() != null
                ? engineGuard.requireKnownRealtimeProvider(pipelineMode, request.realtimeProvider() != null
                        ? request.realtimeProvider() : existing.speechEngine().realtimeProvider())
                : existing.speechEngine().realtimeProvider();

        String llmModel = (request.llmModel() != null)
                ? engineGuard.requireKnownModel(pipelineMode, realtimeProvider, request.llmModel())
                : existing.llmModel();

        String fastLlmModel = (request.fastLlmModel() != null)
                ? engineGuard.requireKnownModel(pipelineMode, realtimeProvider, request.fastLlmModel())
                : existing.fastLlmModel();

        Map<String, String> languageVoices = (request.languageVoices() != null)
                ? engineGuard.requireKnownVoicePerLanguage(companyId, pipelineMode, request.languageVoices())
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
        String sttProvider = request.sttProvider() != null
                ? engineGuard.requireKnownSttProvider(request.sttProvider()) : speechEngine.sttProvider();
        String ttsProvider = request.ttsProvider() != null
                ? engineGuard.requireKnownTtsProvider(request.ttsProvider()) : speechEngine.ttsProvider();
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
                        request.sttModel() != null
                                ? engineGuard.requireKnownSttModel(sttProvider, request.sttModel()) : speechEngine.sttModel(),
                        ttsProvider,
                        request.ttsModel() != null
                                ? engineGuard.requireKnownTtsModel(ttsProvider, request.ttsModel()) : speechEngine.ttsModel()),
                new AiAgentVoice(
                        ttsVoice,
                        request.voiceSpeed() != null ? request.voiceSpeed() : voice.speed(),
                        request.voiceStability() != null ? request.voiceStability() : voice.stability(),
                        request.voiceSimilarityBoost() != null ? request.voiceSimilarityBoost() : voice.similarityBoost(),
                        languageVoices,
                        request.languageVoiceSpeeds() != null ? request.languageVoiceSpeeds() : voice.perLanguageSpeed(),
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
                        request.transferMessage() != null ? request.transferMessage() : behaviour.transferMessage(),
                        request.transferQueueId() != null ? request.transferQueueId() : behaviour.transferQueueId(),
                        request.preConversationAudioFileId() != null
                                ? request.preConversationAudioFileId() : behaviour.preConversationAudioFileId()),
                new AiAgentLimits(
                        request.maxConversationDurationSeconds() != null ? request.maxConversationDurationSeconds() : limits.maxConversationDurationSeconds(),
                        request.silenceEndCallTimeoutSeconds() != null ? request.silenceEndCallTimeoutSeconds() : limits.silenceEndCallTimeoutSeconds(),
                        request.turnTimeoutSeconds() != null ? request.turnTimeoutSeconds() : limits.turnTimeoutSeconds(),
                        request.concurrentCallsLimit() != null ? request.concurrentCallsLimit() : limits.concurrentCallsLimit(),
                        request.dailyCallsLimit() != null ? request.dailyCallsLimit() : limits.dailyCallsLimit()),
                new AiAgentDataPolicy(
                        request.zeroPiiRetention() != null ? request.zeroPiiRetention() : dataPolicy.zeroPiiRetention(),
                        request.storeCallAudio() != null ? request.storeCallAudio() : dataPolicy.storeCallAudio(),
                        request.conversationRetentionDays() != null ? request.conversationRetentionDays() : dataPolicy.conversationRetentionDays(),
                        request.realtimeFactsDelivery() != null ? request.realtimeFactsDelivery() : dataPolicy.realtimeFactsDelivery()),
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
                existing.createdBy(),
                existing.publishedVersion(),
                // Not sent: the version just read, so a client that predates it edits as before.
                request.version() != null ? request.version() : existing.version()
        );

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
        List<AiAgentRow> rows = agents.stream()
                .map(agent -> {
                    AiAgentVersion live = published.get(agent.id());
                    return AiAgentRow.of(agent,
                            agent.script().scenarioId() != null ? scenarioNames.get(agent.script().scenarioId()) : null,
                            agent.createdBy() != null ? creatorNames.get(agent.createdBy()) : null,
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
        return new ScenarioDefinition(definition.stages(), definition.factSchema(), definition.tools(),
                definition.outcomeSchema(), definition.rolePrompt(), List.copyOf(rules),
                definition.disclosureText(), definition.factWebhook(), definition.styleExamples(),
                definition.globalNodes());
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
        return AiAgentRow.of(agent, scenarioName, createdByName, publishedVersion, hasDraftChanges);
    }

    /** A trimmed value, or null — a blank engine name means "the configured default". */
    private static String trimmed(String value) {
        return value != null ? value.trim() : null;
    }
}
