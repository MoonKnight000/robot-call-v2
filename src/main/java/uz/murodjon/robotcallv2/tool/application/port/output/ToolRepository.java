package uz.murodjon.robotcallv2.tool.application.port.output;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;

import java.util.List;
import java.util.Optional;

public interface ToolRepository {

    /** Inserts when the tool has no id yet, otherwise replaces the stored row. */
    Tool save(Tool tool);

    Optional<Tool> findByCompanyIdAndId(long companyId, long id);

    List<Tool> findByCompanyId(long companyId);

    PageableData<Tool> search(long companyId, ToolFilter filter);

    void deleteByCompanyIdAndId(long companyId, long id);

    List<Tool> findByCompanyIdAndAgentId(long companyId, long agentId);

    void attachToAgent(long companyId, long agentId, long toolId);

    void detachFromAgent(long companyId, long agentId, long toolId);

    /** How many agents would lose the tool if it were deleted. */
    long countAgentsUsingTool(long companyId, long toolId);
}
