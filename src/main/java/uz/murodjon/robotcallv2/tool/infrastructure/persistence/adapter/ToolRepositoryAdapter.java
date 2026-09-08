package uz.murodjon.robotcallv2.tool.infrastructure.persistence.adapter;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.tool.application.mapper.ToolMapper;
import uz.murodjon.robotcallv2.tool.application.port.output.ToolRepository;
import uz.murodjon.robotcallv2.tool.domain.entity.Tool;
import uz.murodjon.robotcallv2.tool.domain.entity.ToolFilter;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.AiAgentToolEntity;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.AiAgentToolId;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity.ToolEntity;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository.AiAgentToolJpaRepository;
import uz.murodjon.robotcallv2.tool.infrastructure.persistence.repository.ToolJpaRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ToolRepositoryAdapter implements ToolRepository {

    private final ToolJpaRepository toolJpaRepository;
    private final AiAgentToolJpaRepository aiAgentToolJpaRepository;
    private final ToolMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;

    public ToolRepositoryAdapter(
            ToolJpaRepository toolJpaRepository,
            AiAgentToolJpaRepository aiAgentToolJpaRepository,
            ToolMapper mapper,
            CompanyJpaRepository companyJpaRepository,
            AiAgentJpaRepository aiAgentJpaRepository
    ) {
        this.toolJpaRepository = toolJpaRepository;
        this.aiAgentToolJpaRepository = aiAgentToolJpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
    }

    /**
     * The domain object carries every column — an update is built from the stored tool
     * plus the changed fields — so this is a whole-row write either way, and the id
     * decides whether that is an insert or a replacement.
     */
    @Override
    @Transactional
    public Tool save(Tool tool) {
        CompanyEntity company = companyJpaRepository.getReferenceById(tool.companyId());
        return mapper.toTool(toolJpaRepository.save(mapper.toEntity(tool, company)));
    }

    @Override
    public Optional<Tool> findByCompanyIdAndId(long companyId, long id) {
        return toolJpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toTool);
    }

    @Override
    public List<Tool> findByCompanyId(long companyId) {
        return toolJpaRepository.findAllByCompanyId(companyId).stream()
                .map(mapper::toTool)
                .toList();
    }

    @Override
    public PageableData<Tool> search(long companyId, ToolFilter filter) {
        List<Sort.Order> orders = new ArrayList<>();
        if (filter.orders() != null && !filter.orders().isEmpty()) {
            filter.orders().forEach((field, dir) -> {
                Sort.Direction direction = "DESC".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
                orders.add(new Sort.Order(direction, "createdAt".equalsIgnoreCase(field) ? "createdAt" : field));
            });
        }
        if (orders.isEmpty()) {
            orders.add(new Sort.Order(Sort.Direction.DESC, "createdAt"));
        }

        Pageable pageable = PageRequest.of(filter.page(), filter.size(), Sort.by(orders));
        Page<ToolEntity> page;
        if (filter.search() == null || filter.search().isBlank()) {
            page = toolJpaRepository.findAllByCompanyId(companyId, pageable);
        } else {
            String searchPattern = "%" + filter.search().trim().toLowerCase() + "%";
            page = toolJpaRepository.searchTools(companyId, searchPattern, pageable);
        }
        List<Tool> content = page.getContent().stream().map(mapper::toTool).toList();
        return PageableData.of(content, page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        toolJpaRepository.findByCompanyIdAndId(companyId, id).ifPresent(toolJpaRepository::delete);
    }

    @Override
    public List<Tool> findByCompanyIdAndAgentId(long companyId, long agentId) {
        return toolJpaRepository.findToolsByAgentId(companyId, agentId).stream()
                .map(mapper::toTool)
                .toList();
    }

    /**
     * The binding table has no company of its own — it joins two tables that do — so the
     * company is applied here as a condition on the tool, the same way every other read
     * in this adapter is scoped. A tool belonging to someone else simply matches nothing.
     */
    @Override
    @Transactional
    public void attachToAgent(long companyId, long agentId, long toolId) {
        if (toolJpaRepository.findByCompanyIdAndId(companyId, toolId).isEmpty()) {
            return;
        }
        AiAgentToolId id = new AiAgentToolId(agentId, toolId);
        if (!aiAgentToolJpaRepository.existsById(id)) {
            aiAgentToolJpaRepository.save(new AiAgentToolEntity(
                    aiAgentJpaRepository.getReferenceById(agentId),
                    toolJpaRepository.getReferenceById(toolId)));
        }
    }

    @Override
    @Transactional
    public void detachFromAgent(long companyId, long agentId, long toolId) {
        aiAgentToolJpaRepository.deleteAgentTool(companyId, agentId, toolId);
    }

    @Override
    public long countAgentsUsingTool(long companyId, long toolId) {
        return aiAgentToolJpaRepository.countAgentsUsingTool(companyId, toolId);
    }
}
