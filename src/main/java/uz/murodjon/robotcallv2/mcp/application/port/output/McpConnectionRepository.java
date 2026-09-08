package uz.murodjon.robotcallv2.mcp.application.port.output;

import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;

import java.util.List;
import java.util.Optional;

public interface McpConnectionRepository {

    McpConnection save(McpConnection connection);

    List<McpConnection> findByCompanyId(long companyId);

    Optional<McpConnection> findByCompanyIdAndId(long companyId, long id);

    Optional<McpConnection> findByCompanyIdAndName(long companyId, String name);

    void deleteByCompanyIdAndId(long companyId, long id);

    /** The connections an agent may use — the ones a call actually builds tools from. */
    List<McpConnection> findByAgentId(long companyId, long agentId);

    void attachToAgent(long agentId, long connectionId);

    void detachFromAgent(long agentId, long connectionId);
}
