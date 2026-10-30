package uz.murodjon.robotcallv2.integration.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.integration.application.port.output.IntegrationToolRepository;
import uz.murodjon.robotcallv2.integration.domain.entity.IntegrationTool;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CompanyIntegrationToolEntity;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.entity.CompanyIntegrationToolId;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.mapper.IntegrationToolEntityMapper;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository.CompanyIntegrationToolJpaRepository;
import uz.murodjon.robotcallv2.integration.infrastructure.persistence.repository.IntegrationToolJpaRepository;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class IntegrationToolRepositoryAdapter implements IntegrationToolRepository {

    private final IntegrationToolJpaRepository toolJpaRepository;
    private final CompanyIntegrationToolJpaRepository choiceJpaRepository;
    private final IntegrationToolEntityMapper mapper;

    public IntegrationToolRepositoryAdapter(IntegrationToolJpaRepository toolJpaRepository,
                                            CompanyIntegrationToolJpaRepository choiceJpaRepository,
                                            IntegrationToolEntityMapper mapper) {
        this.toolJpaRepository = toolJpaRepository;
        this.choiceJpaRepository = choiceJpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<IntegrationTool> findByProvider(CrmProvider provider) {
        return toolJpaRepository.findByProviderOrderBySortOrderAscIdAsc(provider).stream()
                .map(mapper::toIntegrationTool).toList();
    }

    @Override
    public Optional<IntegrationTool> findById(long id) {
        return toolJpaRepository.findById(id).map(mapper::toIntegrationTool);
    }

    @Override
    public IntegrationTool update(IntegrationTool tool) {
        return toolJpaRepository.findById(tool.id()).map(entity -> {
            mapper.applyEdits(entity, tool);
            entity.setUpdatedAt(Instant.now());
            return mapper.toIntegrationTool(toolJpaRepository.save(entity));
        }).orElse(null);
    }

    @Override
    public IntegrationTool create(IntegrationTool tool) {
        return mapper.toIntegrationTool(toolJpaRepository.save(mapper.toNewEntity(tool)));
    }

    @Override
    public void deleteById(long id) {
        toolJpaRepository.deleteById(id);
    }

    @Override
    public Map<Long, Boolean> findChoices(long companyId) {
        Map<Long, Boolean> choices = new HashMap<>();
        for (CompanyIntegrationToolEntity entity : choiceJpaRepository.findByIdCompanyId(companyId)) {
            choices.put(entity.getId().getToolId(), entity.isEnabled());
        }
        return choices;
    }

    @Override
    public void saveChoice(long companyId, long toolId, boolean enabled) {
        choiceJpaRepository.save(new CompanyIntegrationToolEntity(new CompanyIntegrationToolId(companyId, toolId), enabled));
    }

    @Override
    public void deleteChoice(long companyId, long toolId) {
        choiceJpaRepository.deleteById(new CompanyIntegrationToolId(companyId, toolId));
    }

    @Override
    public int pinCompaniesWithoutChoice(long toolId, boolean enabled) {
        return choiceJpaRepository.pinCompaniesWithoutChoice(toolId, enabled);
    }
}
