package uz.murodjon.robotcallv2.aimodel.application.port.output;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;
import uz.murodjon.robotcallv2.aimodel.domain.entity.OwnLlmEndpoint;

public interface AiModelConfigRepository {

    AiModelConfig findByCompanyId(long companyId);

    AiModelConfig upsert(long companyId, AiModelConfig config);

    /** Sets the company's own LLM, or clears it with null; the other settings stay as they are. */
    AiModelConfig updateOwnLlm(long companyId, OwnLlmEndpoint ownLlm);
}
