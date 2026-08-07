package uz.murodjon.uysotvoice.aimodel.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.DialogProperties;
import uz.murodjon.uysotvoice.aimodel.domain.AiModelConfig;
import uz.murodjon.uysotvoice.aimodel.domain.EffectiveAiModelConfig;
import uz.murodjon.uysotvoice.aimodel.dto.UpdateAiModelConfigRequest;
import uz.murodjon.uysotvoice.aimodel.repository.AiModelConfigRepository;
import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.company.service.CurrentCompany;

/**
 * Per-company AI model overrides (§11 settings) — how much of {@code
 * spring.ai.google.genai.chat.options.*}/{@code DialogProperties} a company may
 * override for its own calls. {@link #findEffectiveByCompanyId} is the runtime hot path
 * {@code DialogEngine} calls once per call; the rest is admin CRUD.
 */
@Service
public class AiModelConfigService {

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

    /** The current company's overrides, or {@code null} if it has never overridden anything. */
    public AiModelConfig findForCurrentCompany() {
        return repository.findByCompanyId(currentCompany.id());
    }

    /** Full replace: a field left out of the request clears that override back to the process default. */
    public AiModelConfig updateForCurrentCompany(UpdateAiModelConfigRequest request) {
        AiModelConfig saved = repository.upsert(currentCompany.id(),
                AiModelConfig.overrides(request.model(), request.temperature(), request.maxOutputTokens(),
                        request.maxCallSeconds(), request.maxTokensPerCall()));
        auditService.record("AI_MODEL_CONFIG_UPDATE", "ai_model_config",
                String.valueOf(saved.companyId()), request.model());
        return saved;
    }

    /**
     * {@code companyId}'s overrides merged over the process defaults — called from
     * {@code DialogEngine.startCall} once per call, cached on the session for the rest
     * of the call's turns. Never throws: a company with no row simply gets the defaults.
     */
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
