package uz.murodjon.robotcallv2.mcp.infrastructure.persistence.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity.CallAttemptEntity;
import uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.repository.CallAttemptJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.mcp.application.mapper.McpToolExecutionMapper;
import uz.murodjon.robotcallv2.mcp.application.port.output.McpToolExecutionRepository;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpToolExecution;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpConnectionEntity;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpToolExecutionEntity;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.repository.McpConnectionJpaRepository;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.repository.McpToolExecutionJpaRepository;

@Component
public class McpToolExecutionRepositoryAdapter implements McpToolExecutionRepository {

    private static final Logger log = LoggerFactory.getLogger(McpToolExecutionRepositoryAdapter.class);

    private final McpToolExecutionJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final McpConnectionJpaRepository mcpConnectionJpaRepository;
    private final CallAttemptJpaRepository callAttemptJpaRepository;
    private final McpToolExecutionMapper mapper;

    public McpToolExecutionRepositoryAdapter(McpToolExecutionJpaRepository jpaRepository,
                                             CompanyJpaRepository companyJpaRepository,
                                             McpConnectionJpaRepository mcpConnectionJpaRepository,
                                             CallAttemptJpaRepository callAttemptJpaRepository,
                                             McpToolExecutionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mcpConnectionJpaRepository = mcpConnectionJpaRepository;
        this.callAttemptJpaRepository = callAttemptJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public void record(McpToolExecution execution) {
        try {
            CompanyEntity company = companyJpaRepository.getReferenceById(execution.companyId());
            jpaRepository.save(mapper.toEntity(execution, company,
                    connectionReference(execution.connectionId()), callAttemptReference(execution.callAttemptId())));
        } catch (Exception e) {
            // This runs on a live call. Losing the log line is a smaller problem than
            // losing the turn it was describing.
            log.warn("MCP execution log failed for {}: {}", execution.toolName(), e.getMessage());
        }
    }

    /** Null when the connection was deleted after the call ran. */
    private McpConnectionEntity connectionReference(Long id) {
        return id != null ? mcpConnectionJpaRepository.getReferenceById(id) : null;
    }

    /** Null for a tool call made outside a call, e.g. a connection test. */
    private CallAttemptEntity callAttemptReference(Long id) {
        return id != null ? callAttemptJpaRepository.getReferenceById(id) : null;
    }
}
