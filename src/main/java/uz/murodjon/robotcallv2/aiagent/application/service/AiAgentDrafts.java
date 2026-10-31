package uz.murodjon.robotcallv2.aiagent.application.service;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentRepository;
import uz.murodjon.robotcallv2.aiagent.application.port.output.AiAgentVersionRepository;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AgentWebhookConfig;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentVersion;
import uz.murodjon.robotcallv2.aiagent.domain.service.AiAgentValidator;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.company.application.port.input.CompanyConfigUseCase;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

/**
 * An agent's draft row as every writer of it sees it — the agent service, the section
 * settings and the version history all read it, check it and write it the same way.
 */
@Component
class AiAgentDrafts {

    private final AiAgentRepository repository;
    private final AiAgentVersionRepository versionRepository;
    /** The company call cap an agent's own duration limit may not exceed. */
    private final AiModelConfigUseCase aiModelConfigUseCase;
    /** Whether the company may run a human-like persona at all. */
    private final CompanyConfigUseCase companyConfigUseCase;

    AiAgentDrafts(AiAgentRepository repository, AiAgentVersionRepository versionRepository,
                  AiModelConfigUseCase aiModelConfigUseCase, CompanyConfigUseCase companyConfigUseCase) {
        this.repository = repository;
        this.versionRepository = versionRepository;
        this.aiModelConfigUseCase = aiModelConfigUseCase;
        this.companyConfigUseCase = companyConfigUseCase;
    }

    AiAgent requireAgent(long companyId, long id) {
        AiAgent agent = repository.findByCompanyIdAndId(companyId, id);
        if (agent == null) {
            throw new NotFoundException(ErrorCode.AI_AGENT_NOT_FOUND, id);
        }
        return agent;
    }

    AiAgentVersion requireVersion(long companyId, long agentId, int version) {
        AiAgentVersion found = versionRepository.findByAgentIdAndVersion(companyId, agentId, version);
        if (found == null) {
            throw new NotFoundException(ErrorCode.AI_AGENT_VERSION_NOT_FOUND, agentId, version);
        }
        return found;
    }

    /** Every write goes through here, whichever screen it came from. */
    AiAgent validate(long companyId, AiAgent agent) {
        AiModelConfig companyConfig = aiModelConfigUseCase.findByCompanyId(companyId);
        AiAgentValidator.validate(agent, companyConfig != null ? companyConfig.maxCallSeconds() : null);
        return agent;
    }

    /** Validates and writes the draft row; 409 when someone saved the agent after {@code agent} was read. */
    void update(long companyId, long id, AiAgent agent) {
        if (!repository.update(companyId, id, validate(companyId, agent))) {
            throw new ConflictException(ErrorCode.ENTITY_VERSION_CONFLICT, "ai_agent", id);
        }
    }

    /**
     * The transfer queue has to be one of the company's own. Checked against the queue
     * table directly rather than through the queue feature, which itself checks agents —
     * the two features would otherwise depend on each other.
     */
    void requireOwnQueue(long companyId, Long queueId) {
        if (queueId != null && !repository.existsCallQueueByCompanyIdAndId(companyId, queueId)) {
            throw new NotFoundException(ErrorCode.CALL_QUEUE_NOT_FOUND, queueId);
        }
    }

    /**
     * A human-like persona skips the §11.1 disclosure, so it is the company's to allow
     * ({@code CompanyConfig#humanLikePersonaAllowed}), not the agent editor's. Only a
     * request that asks for it is checked: an agent saved before the company turned it off
     * can still be edited, and speaks with the disclosure until it is changed. A rollback
     * asks for the persona of the version it restores.
     */
    void requirePersonaAllowed(long companyId, AgentPersona requested) {
        if (requested != AgentPersona.HUMAN_LIKE) {
            return;
        }
        CompanyConfig config = companyConfigUseCase.find(companyId);
        if (config != null && !config.humanLikePersonaAllowed()) {
            throw new ConflictException(ErrorCode.AI_AGENT_HUMAN_LIKE_NOT_ALLOWED);
        }
    }

    /**
     * Stripping personal data while keeping the recording keeps the very thing the setting
     * was meant to remove. Checked on every screen that can set either flag — create, the
     * full edit and the advanced section — not only on the one that shows both.
     */
    static void requireCompatibleDataPolicy(boolean zeroPiiRetention, boolean storeCallAudio) {
        if (zeroPiiRetention && storeCallAudio) {
            throw new ValidationException(ErrorCode.ZERO_PII_STORE_AUDIO_CONFLICT);
        }
    }

    /**
     * Rejects a webhook aimed at this network while its author is still looking at the
     * form, rather than only when the call it belongs to has already happened.
     *
     * <p>An address holding a {@code {{secrets.KEY}}} placeholder is not a URL yet and is
     * left alone; what it resolves to is screened by {@code AgentWebhookExecutor} on every
     * delivery, which is where the real defence is.
     */
    static void requireCallableWebhookUrls(AgentWebhookConfig... configs) {
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
}
