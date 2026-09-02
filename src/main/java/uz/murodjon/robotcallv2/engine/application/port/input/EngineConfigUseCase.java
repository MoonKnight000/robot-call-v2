package uz.murodjon.robotcallv2.engine.application.port.input;

import uz.murodjon.robotcallv2.engine.application.dto.EngineOptions;
import uz.murodjon.robotcallv2.engine.application.dto.UpdateEngineConfigRequest;
import uz.murodjon.robotcallv2.engine.domain.entity.EffectiveEngineConfig;
import uz.murodjon.robotcallv2.engine.domain.entity.EngineConfig;

public interface EngineConfigUseCase {

    EngineConfig findForCurrentCompany();

    EffectiveEngineConfig findEffectiveForCurrentCompany();

    EngineOptions findOptions();

    EngineConfig updateForCurrentCompany(UpdateEngineConfigRequest request);

    EffectiveEngineConfig findEffectiveByCompanyId(long companyId);
}
