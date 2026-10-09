package uz.murodjon.robotcallv2.aimodel.application.service;

import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateOwnLlmRequest;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelConfigRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.aimodel.domain.service.AiModelConfigValidator;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.secret.application.port.input.SecretUseCase;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.URI;

/**
 * Per-company AI model overrides (§11 settings).
 */
@Service
public class AiModelConfigService implements AiModelConfigUseCase {

    private final AiModelConfigRepository repository;
    private final AuditUseCase auditService;
    private final DialogProperties dialogProperties;
    private final AiModelUseCase aiModelUseCase;
    private final SecretUseCase secretUseCase;

    public AiModelConfigService(AiModelConfigRepository repository, AuditUseCase auditService,
                                DialogProperties dialogProperties, AiModelUseCase aiModelUseCase,
                                SecretUseCase secretUseCase) {
        this.repository = repository;
        this.auditService = auditService;
        this.dialogProperties = dialogProperties;
        this.aiModelUseCase = aiModelUseCase;
        this.secretUseCase = secretUseCase;
    }

    @Override
    public AiModelConfig findByCompanyId(long companyId) {
        return repository.findByCompanyId(companyId);
    }

    @Override
    public AiModelConfig updateByCompanyId(long companyId, UpdateAiModelConfigRequest request) {
        AiModelConfigValidator.validate(request.temperature(), request.maxOutputTokens(),
                request.maxCallSeconds(), request.maxTokensPerCall());
        AiModelConfig saved = repository.upsert(companyId,
                AiModelConfig.overrides(requireKnownModel(request.model()),
                        request.temperature(), request.maxOutputTokens(),
                        request.maxCallSeconds(), request.maxTokensPerCall()));
        auditService.record(companyId, "AI_MODEL_CONFIG_UPDATE", "ai_model_config",
                String.valueOf(saved.companyId()), request.model());
        return saved;
    }

    @Override
    public AiModelConfig updateOwnLlm(long companyId, UpdateOwnLlmRequest request) {
        String baseUrl = request.baseUrl().trim().replaceAll("/+$", "");
        // Screened here so a bad address is a 400 now — and again on every request, by the pinned
        // client the calls use, because what a host resolves to can change after it was saved.
        URI address = PublicUrlGuard.parsePublic(baseUrl);
        if (address == null || !"https".equalsIgnoreCase(address.getScheme())) {
            throw new ValidationException(ErrorCode.OWN_LLM_BASE_URL_INVALID);
        }
        String apiKeySecret = request.apiKeySecret().trim();
        boolean secretExists = secretUseCase.findSecrets(companyId).stream()
                .anyMatch(secret -> secret.key().equals(apiKeySecret));
        if (!secretExists) {
            throw new ValidationException(ErrorCode.OWN_LLM_API_KEY_SECRET_UNKNOWN, apiKeySecret);
        }
        String fastModel = request.fastModel() == null || request.fastModel().isBlank()
                ? null : request.fastModel().trim();
        AiModelConfig saved = repository.updateOwnLlm(companyId,
                new OwnLlmEndpoint(baseUrl, request.model().trim(), fastModel, apiKeySecret));
        auditService.record(companyId, "AI_MODEL_OWN_LLM_UPDATE", "ai_model_config",
                String.valueOf(saved.companyId()), baseUrl);
        return saved;
    }

    @Override
    public AiModelConfig deleteOwnLlm(long companyId) {
        AiModelConfig saved = repository.updateOwnLlm(companyId, null);
        auditService.record(companyId, "AI_MODEL_OWN_LLM_DELETE", "ai_model_config",
                String.valueOf(saved.companyId()), null);
        return saved;
    }

    @Override
    public EffectiveAiModelConfig findEffectiveByCompanyId(long companyId) {
        AiModelConfig config = repository.findByCompanyId(companyId);
        if (config == null) {
            return new EffectiveAiModelConfig(null, null, null,
                    dialogProperties.maxCallSeconds(), dialogProperties.maxTokensPerCall());
        }
        int maxCallSeconds = config.maxCallSeconds() != null
                ? config.maxCallSeconds() : dialogProperties.maxCallSeconds();
        long maxTokensPerCall = config.maxTokensPerCall() != null
                ? config.maxTokensPerCall() : dialogProperties.maxTokensPerCall();
        return new EffectiveAiModelConfig(config.model(), config.temperature(), config.maxOutputTokens(),
                maxCallSeconds, maxTokensPerCall, config.ownLlm());
    }

    /**
     * The model every call in this company runs on, or null to stay on the deployment's
     * own default. Checked against the catalog so a typo is a 400 here instead of a call
     * that connects and then fails on the first turn.
     */
    private String requireKnownModel(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        String trimmed = model.trim();
        // No mode to narrow by: this is the company-wide default an agent inherits when it
        // names no model of its own, and the agent it lands on may run either pipeline.
        if (!aiModelUseCase.isSelectable(AiModelKind.LLM, null, trimmed)) {
            throw new ValidationException(ErrorCode.AI_MODEL_UNKNOWN, trimmed,
                    aiModelUseCase.findSelectableIds(AiModelKind.LLM, null));
        }
        return trimmed;
    }
}
