package uz.murodjon.robotcallv2.tool.application.port.input;

import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.tool.application.dto.*;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;

import java.util.List;

public interface ToolUseCase {

    ToolRow createTool(long companyId, CreateToolRequest request);

    /** Creates every one or none of them — one transaction. */
    List<ToolRow> createTools(long companyId, List<CreateToolRequest> requests);

    ToolRow updateTool(long companyId, long id, UpdateToolRequest request);

    ToolRow findTool(long companyId, long id);

    List<ToolRow> findTools(long companyId);

    PageableData<ToolRow> filterTools(long companyId, ToolFilter filter);

    void deleteTool(long companyId, long id);

    List<ToolRow> findAgentTools(long companyId, long agentId);

    void attachTool(long companyId, long agentId, long toolId);

    void detachTool(long companyId, long agentId, long toolId);

    /** The bound tools as the dialog needs them — full domain rows, not a screen's view. */
    List<Tool> findAgentToolDefinitions(long companyId, long agentId);

    /** Runs the tool once with the given arguments, as the model would, and returns what it would see. */
    ToolTestResponse testTool(long companyId, long id, TestToolRequest request);
}
