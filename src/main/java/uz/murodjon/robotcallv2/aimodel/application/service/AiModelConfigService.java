package uz.murodjon.robotcallv2.aimodel.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelConfigRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.service.AiModelConfigValidator;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

/**
 * Per-company AI model overrides (§11 settings).
 */
@Service
public class AiModelConfigService implements AiModelConfigUseCase {

    private final AiModelConfigRepository repository;
    private final AuditService auditService;
    private final DialogProperties dialogProperties;
    private final AiModelUseCase aiModelUseCase;

    public AiModelConfigService(AiModelConfigRepository repository, AuditService auditService,
                                DialogProperties dialogProperties, AiModelUseCase aiModelUseCase) {
        this.repository = repository;
        this.auditService = auditService;
        this.dialogProperties = dialogProperties;
        this.aiModelUseCase = aiModelUseCase;
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
                AiModelConfig.overrides(requireKnownModel(companyId, request.model()),
                        request.temperature(), request.maxOutputTokens(),
                        request.maxCallSeconds(), request.maxTokensPerCall()));
        auditService.record(companyId, "AI_MODEL_CONFIG_UPDATE", "ai_model_config",
                String.valueOf(saved.companyId()), request.model());
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
                maxCallSeconds, maxTokensPerCall);
    }

    /**
     * The model every call in this company runs on, or null to stay on the deployment's
     * own default. Checked against the catalog so a typo is a 400 here instead of a call
     * that connects and then fails on the first turn.
     */
    private String requireKnownModel(long companyId, String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        String trimmed = model.trim();
        if (!aiModelUseCase.isSelectable(companyId, trimmed)) {
            throw new ValidationException(ErrorCode.AI_MODEL_UNKNOWN, trimmed,
                    aiModelUseCase.findSelectableIds(companyId));
        }
        return trimmed;
    }
}
