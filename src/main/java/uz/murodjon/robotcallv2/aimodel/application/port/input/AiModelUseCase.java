package uz.murodjon.robotcallv2.aimodel.application.port.input;

import uz.murodjon.robotcallv2.aimodel.domain.entity.AiModel;

import java.util.List;

public interface AiModelUseCase {

    List<AiModel> findSelectableByCompanyId(long companyId);

    boolean isSelectable(long companyId, String id);

    List<String> findSelectableIds(long companyId);
}
