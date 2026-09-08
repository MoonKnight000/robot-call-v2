package uz.murodjon.robotcallv2.mcp.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.mcp.application.mapper.McpConnectionMapper;
import uz.murodjon.robotcallv2.mcp.application.port.output.McpConnectionRepository;
import uz.murodjon.robotcallv2.mcp.domain.entity.McpConnection;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.entity.McpConnectionEntity;
import uz.murodjon.robotcallv2.mcp.infrastructure.persistence.repository.McpConnectionJpaRepository;

import java.util.List;
import java.util.Optional;

@Component
public class McpConnectionRepositoryAdapter implements McpConnectionRepository {

    private final McpConnectionJpaRepository jpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final McpConnectionMapper mapper;

    public McpConnectionRepositoryAdapter(McpConnectionJpaRepository jpaRepository,
                                          CompanyJpaRepository companyJpaRepository,
                                          McpConnectionMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.companyJpaRepository = companyJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public McpConnection save(McpConnection connection) {
        CompanyEntity company = companyJpaRepository.getReferenceById(connection.companyId());
        return mapper.toMcpConnection(jpaRepository.save(mapper.toEntity(connection, company)));
    }

    @Override
    public List<McpConnection> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdOrderByNameAsc(companyId).stream()
                .map(mapper::toMcpConnection)
                .toList();
    }

    @Override
    public Optional<McpConnection> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toMcpConnection);
    }

    @Override
    public Optional<McpConnection> findByCompanyIdAndName(long companyId, String name) {
        return jpaRepository.findByCompanyIdAndName(companyId, name).map(mapper::toMcpConnection);
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }

    @Override
    public List<McpConnection> findByAgentId(long companyId, long agentId) {
        return jpaRepository.findByAgentId(companyId, agentId).stream()
                .map(mapper::toMcpConnection)
                .toList();
    }

    @Override
    @Transactional
    public void attachToAgent(long agentId, long connectionId) {
        jpaRepository.attachToAgent(agentId, connectionId);
    }

    @Override
    @Transactional
    public void detachFromAgent(long agentId, long connectionId) {
        jpaRepository.detachFromAgent(agentId, connectionId);
    }
}
