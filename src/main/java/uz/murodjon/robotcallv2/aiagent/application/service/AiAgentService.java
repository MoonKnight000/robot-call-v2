package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.aiagent.application.dto.AiAgentRow;
import uz.murodjon.robotcallv2.aiagent.application.dto.CreateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.dto.UpdateAiAgentRequest;
import uz.murodjon.robotcallv2.aiagent.application.port.input.AiAgentUseCase;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentFilter;
import uz.murodjon.robotcallv2.aiagent.domain.enums.AmbientSound;
import uz.murodjon.robotcallv2.aiagent.domain.enums.VoicemailAction;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentValidator;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CompanyConfigService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.service.UserService;
import uz.murodjon.robotcallv2.voice.application.port.input.TtsVoiceUseCase;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AI agents — the voice, persona and model a scenario is spoken with (V12).
 *
 * <p>This is where the voice checks that used to live in {@code CampaignService} now sit,
 * for the same reason the columns moved: an inbound call has no campaign, so a validation
 * that only ran on campaign create left inbound calls able to speak with a voice that does
 * not exist. Every call, in either direction, is now created through an agent.
 */
@Service
public class AiAgentService implements AiAgentUseCase {

    private final AiAgentRepository repository;
    private final ScenarioUseCase scenarioUseCase;
    private final TtsVoiceUseCase ttsVoiceUseCase;
    private final CompanyConfigService companyConfigService;
    private final UserService userService;
    private final CurrentCompany currentCompany;
    private final AuditService auditService;

    public AiAgentService(AiAgentRepository repository, ScenarioUseCase scenarioUseCase,
                          TtsVoiceUseCase ttsVoiceUseCase, CompanyConfigService companyConfigService,
                          UserService userService, CurrentCompany currentCompany, AuditService auditService) {
        this.repository = repository;
        this.scenarioUseCase = scenarioUseCase;
        this.ttsVoiceUseCase = ttsVoiceUseCase;
        this.companyConfigService = companyConfigService;
        this.userService = userService;
        this.currentCompany = currentCompany;
        this.auditService = auditService;
    }

    @Override
    public AiAgentRow createAgent(CreateAiAgentRequest request) {
        long companyId = currentCompany.id();
        scenarioUseCase.requireScenario(request.scenarioId());
        AiAgentValidator.validate(request.temperature(), request.maxOutputTokens());
        AiAgent agent = new AiAgent(
                0,
                companyId,
                request.name().trim(),
                request.description(),
                request.scenarioId(),
                companyConfigService.resolveLanguage(companyId, request.language()),
                requireKnownVoice(request.ttsVoice()),
                requireKnownVoicePerLanguage(companyId, request.languageVoices()),
                request.persona() != null ? request.persona() : AgentPersona.AI_ASSISTANT,
                blankToNull(request.llmModel()),
                request.temperature(),
                request.maxOutputTokens(),
                request.ambientSound() != null ? request.ambientSound() : AmbientSound.OFF,
                request.emotionAdaptiveVoice() == null || request.emotionAdaptiveVoice(),
                request.dtmfInputEnabled() != null && request.dtmfInputEnabled(),
                request.voicemailAction() != null ? request.voicemailAction() : VoicemailAction.HANGUP,
                request.voicemailMessage(),
                request.midCallSmsEnabled() != null && request.midCallSmsEnabled(),
                request.midCallSmsTemplate(),
                request.sipTrunkIds() != null ? request.sipTrunkIds() : Set.of(),
                request.enabled() == null || request.enabled(),
                null,
                null);
        long id = repository.create(agent);
        auditService.record("AI_AGENT_CREATE", "ai_agent", String.valueOf(id),
                agent.name() + " (scenario " + agent.scenarioId() + ", " + agent.language() + ")");
        return findAgentRow(id);
    }

    @Override
    public AiAgentRow updateAgent(long id, UpdateAiAgentRequest request) {
        long companyId = currentCompany.id();
        AiAgent existing = requireAgent(companyId, id);
        scenarioUseCase.requireScenario(request.scenarioId());
        AiAgentValidator.validate(request.temperature(), request.maxOutputTokens());
        AiAgent agent = new AiAgent(
                id,
                companyId,
                request.name().trim(),
                request.description(),
                request.scenarioId(),
                companyConfigService.resolveLanguage(companyId, request.language()),
                requireKnownVoice(request.ttsVoice()),
                requireKnownVoicePerLanguage(companyId, request.languageVoices()),
                request.persona() != null ? request.persona() : AgentPersona.AI_ASSISTANT,
                blankToNull(request.llmModel()),
                request.temperature(),
                request.maxOutputTokens(),
                request.ambientSound() != null ? request.ambientSound() : AmbientSound.OFF,
                request.emotionAdaptiveVoice() == null || request.emotionAdaptiveVoice(),
                request.dtmfInputEnabled() != null && request.dtmfInputEnabled(),
                request.voicemailAction() != null ? request.voicemailAction() : VoicemailAction.HANGUP,
                request.voicemailMessage(),
                request.midCallSmsEnabled() != null && request.midCallSmsEnabled(),
                request.midCallSmsTemplate(),
                request.sipTrunkIds() != null ? request.sipTrunkIds() : Set.of(),
                request.enabled() == null || request.enabled(),
                existing.createdAt(),
                existing.createdBy());
        repository.update(companyId, id, agent);
        auditService.record("AI_AGENT_UPDATE", "ai_agent", String.valueOf(id), agent.name());
        return findAgentRow(id);
    }

    @Override
    public AiAgentRow findAgentRow(long id) {
        AiAgent agent = requireAgent(currentCompany.id(), id);
        String scenarioName = scenarioUseCase.scenarioNamesByIds(List.of(agent.scenarioId()))
                .get(agent.scenarioId());
        String createdByName = agent.createdBy() != null
                ? userService.namesByIds(List.of(agent.createdBy())).get(agent.createdBy())
                : null;
        return AiAgentRow.of(agent, scenarioName, createdByName);
    }

    @Override
    public PageableData<AiAgentRow> filterAgents(AiAgentFilter filter) {
        long companyId = currentCompany.id();
        List<AiAgent> agents = repository.findAll(companyId, filter);
        long total = repository.count(companyId, filter);
        Map<Long, String> scenarioNames = scenarioUseCase.scenarioNamesByIds(
                agents.stream().map(AiAgent::scenarioId).collect(Collectors.toSet()));
        Map<Long, String> creatorNames = userService.namesByIds(
                agents.stream().map(AiAgent::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<AiAgentRow> rows = agents.stream()
                .map(agent -> AiAgentRow.of(agent, scenarioNames.get(agent.scenarioId()),
                        agent.createdBy() != null ? creatorNames.get(agent.createdBy()) : null))
                .toList();
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    @Override
    public void deleteAgent(long id) {
        long companyId = currentCompany.id();
        requireAgent(companyId, id);
        // Refused rather than cascaded: deleting an agent a campaign still runs would leave
        // that campaign unable to place a single call, and the owner would find out by
        // watching it dial nothing.
        long campaigns = repository.countCampaignsUsing(id);
        long routes = repository.countInboundRoutesUsing(id);
        if (campaigns > 0 || routes > 0) {
            throw new ConflictException(ErrorCode.AI_AGENT_IN_USE, id, campaigns, routes);
        }
        repository.delete(companyId, id);
        auditService.record("AI_AGENT_DELETE", "ai_agent", String.valueOf(id), null);
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
    public Map<Long, String> findNamesByIds(Collection<Long> ids) {
        return repository.findNamesByIds(ids);
    }

    private Map<String, String> requireKnownVoicePerLanguage(long companyId, Map<String, String> languageVoices) {
        if (languageVoices == null || languageVoices.isEmpty()) {
            return Map.of();
        }
        Map<String, String> checked = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : languageVoices.entrySet()) {
            String language = companyConfigService.resolveLanguage(companyId, entry.getKey());
            String voiceId = requireKnownVoice(entry.getValue());
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

    private String requireKnownVoice(String ttsVoice) {
        if (ttsVoice == null || ttsVoice.isBlank()) {
            return null;
        }
        String trimmed = ttsVoice.trim();
        if (!ttsVoiceUseCase.isSelectable(trimmed)) {
            throw new ValidationException(ErrorCode.TTS_VOICE_UNKNOWN, trimmed, ttsVoiceUseCase.selectableIds());
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
