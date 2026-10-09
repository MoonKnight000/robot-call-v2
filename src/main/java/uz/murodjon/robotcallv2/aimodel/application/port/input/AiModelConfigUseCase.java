package uz.murodjon.robotcallv2.aimodel.application.port.input;

import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateAiModelConfigRequest;
import uz.murodjon.robotcallv2.aimodel.application.dto.UpdateOwnLlmRequest;
import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.EffectiveAiModelConfig;

public interface AiModelConfigUseCase {

    AiModelConfig findByCompanyId(long companyId);

    AiModelConfig updateByCompanyId(long companyId, UpdateAiModelConfigRequest request);

    /** Moves the company's calls and summaries onto its own OpenAI-compatible LLM. */
    AiModelConfig updateOwnLlm(long companyId, UpdateOwnLlmRequest request);

    /** Back to the platform's model. */
    AiModelConfig deleteOwnLlm(long companyId);

    EffectiveAiModelConfig findEffectiveByCompanyId(long companyId);
}
