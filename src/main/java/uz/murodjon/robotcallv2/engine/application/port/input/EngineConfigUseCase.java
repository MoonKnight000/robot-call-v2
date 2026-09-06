package uz.murodjon.robotcallv2.engine.application.port.input;

import uz.murodjon.robotcallv2.engine.application.dto.EngineOptions;
import uz.murodjon.robotcallv2.engine.application.dto.UpdateEngineConfigRequest;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;

public interface EngineConfigUseCase {

    EngineConfig findByCompanyId(long companyId);

    EffectiveEngineConfig findEffectiveByCompanyId(long companyId);

    EngineOptions findOptions();

    EngineConfig updateByCompanyId(long companyId, UpdateEngineConfigRequest request);
}
