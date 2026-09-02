package uz.murodjon.robotcallv2.aimodel.application.port.output;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModelConfig;

public interface AiModelConfigRepository {

    AiModelConfig findByCompanyId(long companyId);

    AiModelConfig upsert(long companyId, AiModelConfig config);
}
