package uz.murodjon.robotcallv2.aimodel.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.dialog.DialogProperties;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelConfigUseCase;
import uz.murodjon.robotcallv2.aimodel.application.port.output.AiModelConfigRepository;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.service.AiModelConfigValidator;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;

/**
 * Per-company AI model overrides (§11 settings).
 */
@Service
public class AiModelConfigService implements AiModelConfigUseCase {

    private final AiModelConfigRepository repository;
    private final AuditService auditService;
    private final DialogProperties dialogProperties;
    private final CurrentCompany currentCompany;

    public AiModelConfigService(AiModelConfigRepository repository, AuditService auditService,
                                DialogProperties dialogProperties, CurrentCompany currentCompany) {
        this.repository = repository;
        this.auditService = auditService;
        this.dialogProperties = dialogProperties;
        this.currentCompany = currentCompany;
    }

    @Override
    public AiModelConfig findForCurrentCompany() {
        return repository.findByCompanyId(currentCompany.id());
    }

    @Override
    public AiModelConfig updateForCurrentCompany(UpdateAiModelConfigRequest request) {
        AiModelConfigValidator.validate(request.temperature(), request.maxOutputTokens(),
                request.maxCallSeconds(), request.maxTokensPerCall());
        AiModelConfig saved = repository.upsert(currentCompany.id(),
                AiModelConfig.overrides(request.model(), request.temperature(), request.maxOutputTokens(),
                        request.maxCallSeconds(), request.maxTokensPerCall()));
        auditService.record("AI_MODEL_CONFIG_UPDATE", "ai_model_config",
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
}
