package uz.murodjon.uysotvoice.aimodel.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.agent.dialog.DialogProperties;
import uz.murodjon.uysotvoice.aimodel.dto.AiModelConfig;
import uz.murodjon.uysotvoice.aimodel.dto.EffectiveAiModelConfig;
import uz.murodjon.uysotvoice.aimodel.dto.UpdateAiModelConfigRequest;
import uz.murodjon.uysotvoice.aimodel.repository.AiModelConfigRepository;
import uz.murodjon.uysotvoice.audit.service.AuditService;

/**
 * Per-company AI model overrides (§11 settings) — how much of {@code
 * spring.ai.google.genai.chat.options.*}/{@code DialogProperties} a company may
 * override for its own calls. {@link #effective} is the runtime hot path {@code
 * DialogEngine} calls once per call; the rest is admin CRUD.
 */
@Service
public class AiModelConfigService {

    private final AiModelConfigRepository repo;
    private final AuditService audit;
    private final DialogProperties defaults;

    public AiModelConfigService(AiModelConfigRepository repo, AuditService audit, DialogProperties defaults) {
        this.repo = repo;
        this.audit = audit;
        this.defaults = defaults;
    }

    /** The current company's row, or {@code null} if it has never overridden anything. */
    public AiModelConfig find() {
        return repo.find();
    }

    public AiModelConfig update(UpdateAiModelConfigRequest r) {
        AiModelConfig row = repo.save(r.model(), r.temperature(), r.maxOutputTokens(),
                r.maxCallSeconds(), r.maxTokensPerCall());
        audit.record("AI_MODEL_CONFIG_UPDATE", "ai_model_config", String.valueOf(row.companyId()), r.model());
        return row;
    }

    /**
     * {@code companyId}'s overrides merged over the process defaults — called from
     * {@code DialogEngine.startCall} once per call, cached on the session for the rest
     * of the call's turns. Never throws: a company with no row simply gets the defaults.
     */
    public EffectiveAiModelConfig effective(long companyId) {
        AiModelConfig row = repo.find(companyId);
        if (row == null) {
            return new EffectiveAiModelConfig(null, null, null, defaults.maxCallSeconds(), defaults.maxTokensPerCall());
        }
        int maxCallSeconds = row.maxCallSeconds() != null ? row.maxCallSeconds() : defaults.maxCallSeconds();
        long maxTokensPerCall = row.maxTokensPerCall() != null ? row.maxTokensPerCall() : defaults.maxTokensPerCall();
        return new EffectiveAiModelConfig(row.model(), row.temperature(), row.maxOutputTokens(),
                maxCallSeconds, maxTokensPerCall);
    }
}
