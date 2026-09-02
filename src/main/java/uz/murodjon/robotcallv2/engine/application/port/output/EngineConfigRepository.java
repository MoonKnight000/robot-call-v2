package uz.murodjon.robotcallv2.engine.application.port.output;

import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;

public interface EngineConfigRepository {

    EngineConfig findByCompanyId(long companyId);

    EngineConfig upsert(long companyId, EngineConfig config);
}
