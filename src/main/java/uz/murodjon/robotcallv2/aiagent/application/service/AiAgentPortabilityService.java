package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentExportFile;
import uz.murodjon.robotcallv2.aiagent.application.dto.AgentImportResponse;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentPortabilityUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentSpeechEngine;
import uz.murodjon.robotcallv2.aiagent.domain.entity.ImportedAgent;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentPortability;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentSnapshot;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.time.Instant;

/**
 * Agent export and import (docs/api/ai-agents.md §14.6). An import is a new draft, checked the
 * way a created agent is: voices and models must exist on this server, the persona must be
 * allowed here, and the settings must pass the agent validator.
 */
@Service
public class AiAgentPortabilityService implements AiAgentPortabilityUseCase {

    static final String FILE_KIND = "robotcall.ai-agent";
    static final int FILE_FORMAT = 1;
    private static final String UNNAMED = "Import qilingan agent";

    private final AiAgentUseCase aiAgentUseCase;
    private final AiAgentRepository repository;
    private final AiAgentDrafts drafts;
    private final AiAgentEngineGuard engineGuard;
    private final AuditUseCase auditUseCase;
    private final CurrentUser currentUser;

    public AiAgentPortabilityService(AiAgentUseCase aiAgentUseCase, AiAgentRepository repository,
                                     AiAgentDrafts drafts, AiAgentEngineGuard engineGuard,
                                     AuditUseCase auditUseCase, CurrentUser currentUser) {
        this.aiAgentUseCase = aiAgentUseCase;
        this.repository = repository;
        this.drafts = drafts;
        this.engineGuard = engineGuard;
        this.auditUseCase = auditUseCase;
        this.currentUser = currentUser;
    }

    @Override
    public AgentExportFile exportAgent(long companyId, long id) {
        AiAgent agent = aiAgentUseCase.requireAgent(companyId, id);
        ScenarioDefinition sharedScenario = agent.script().scenarioId() != null
                ? aiAgentUseCase.resolveScenario(companyId, agent).definition()
                : null;
        AiAgent exported = AiAgentPortability.toExported(agent, sharedScenario);
        auditUseCase.record(companyId, "AI_AGENT_EXPORT", "ai_agent", String.valueOf(id), agent.name());
        return new AgentExportFile(FILE_KIND, FILE_FORMAT, Instant.now(), AiAgentSnapshot.toMap(exported));
    }

    @Override
    @Transactional
    public AgentImportResponse importAgent(long companyId, AgentExportFile file) {
        if (!FILE_KIND.equals(file.kind())) {
            throw new ValidationException(ErrorCode.AI_AGENT_IMPORT_INVALID, "kind " + file.kind());
        }
        if (file.format() == null || file.format() != FILE_FORMAT) {
            throw new ValidationException(ErrorCode.AI_AGENT_IMPORT_INVALID, "format " + file.format());
        }
        AiAgent source = AiAgentSnapshot.fromMap(file.agent());
        String name = source.name() == null || source.name().isBlank() ? UNNAMED : source.name().trim();
        ImportedAgent imported = AiAgentPortability.toImported(source, companyId, name, Instant.now(),
                currentUser.id().orElse(null));
        AiAgent agent = imported.agent();

        AiAgentSpeechEngine engine = agent.speechEngine();
        PipelineMode mode = engine.mode();
        engineGuard.requireKnownSttProvider(engine.sttProvider());
        engineGuard.requireKnownTtsProvider(engine.ttsProvider());
        engineGuard.requireKnownRealtimeProvider(mode, engine.realtimeProvider());
        engineGuard.requireKnownVoice(companyId, mode, agent.voice().defaultVoice());
        engineGuard.requireKnownVoicePerLanguage(companyId, mode, agent.voice().perLanguage());
        engineGuard.requireKnownVoice(companyId, PipelineMode.CASCADE, agent.voice().fallbackVoice());
        engineGuard.requireKnownModel(mode, engine.realtimeProvider(), agent.llmModel());
        engineGuard.requireKnownFastModel(agent.fastLlmModel());
        if (agent.llmFallback() != null) {
            engineGuard.requireKnownFallbackModel(agent.llmFallback().model());
        }
        drafts.requirePersonaAllowed(companyId, agent.persona());

        long id = repository.create(drafts.validate(companyId, agent));
        auditUseCase.record(companyId, "AI_AGENT_IMPORT", "ai_agent", String.valueOf(id), name);
        return new AgentImportResponse(aiAgentUseCase.findAgentRow(companyId, id), imported.removed());
    }
}
