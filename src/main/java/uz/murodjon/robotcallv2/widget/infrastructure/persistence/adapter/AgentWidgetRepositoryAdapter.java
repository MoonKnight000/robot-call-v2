package uz.murodjon.robotcallv2.widget.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.repository.AiAgentJpaRepository;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.widget.application.port.output.AgentWidgetRepository;
import uz.murodjon.robotcallv2.widget.domain.entity.AgentWidget;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.entity.AgentWidgetEntity;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.mapper.AgentWidgetEntityMapper;
import uz.murodjon.robotcallv2.widget.infrastructure.persistence.repository.AgentWidgetJpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
public class AgentWidgetRepositoryAdapter implements AgentWidgetRepository {

    private final AgentWidgetJpaRepository jpaRepository;
    private final AgentWidgetEntityMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;
    private final AiAgentJpaRepository aiAgentJpaRepository;

    public AgentWidgetRepositoryAdapter(AgentWidgetJpaRepository jpaRepository,
                                        AgentWidgetEntityMapper mapper,
                                        CompanyJpaRepository companyJpaRepository,
                                        AiAgentJpaRepository aiAgentJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
        this.aiAgentJpaRepository = aiAgentJpaRepository;
    }

    @Override
    @Transactional
    public AgentWidget save(AgentWidget widget) {
        AgentWidgetEntity entity;
        if (widget.id() > 0) {
            entity = jpaRepository.findByCompanyIdAndId(widget.companyId(), widget.id())
                    .orElseGet(AgentWidgetEntity::new);
        } else {
            entity = new AgentWidgetEntity();
            entity.setCreatedAt(widget.createdAt() != null ? widget.createdAt() : Instant.now());
        }

        entity.setCompany(companyJpaRepository.getReferenceById(widget.companyId()));
        entity.setAgent(aiAgentJpaRepository.getReferenceById(widget.agentId()));
        mapper.applyToEntity(widget, entity);
        entity.setUpdatedAt(Instant.now());

        AgentWidgetEntity saved = jpaRepository.save(entity);
        return mapper.toAgentWidget(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentWidget> findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toAgentWidget);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AgentWidget> findByWidgetKey(String widgetKey) {
        return jpaRepository.findByWidgetKey(widgetKey).map(mapper::toAgentWidget);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentWidget> findByCompanyIdAndAgentId(long companyId, long agentId) {
        return jpaRepository.findByCompanyIdAndAgentIdOrderByCreatedAtDesc(companyId, agentId)
                .stream()
                .map(mapper::toAgentWidget)
                .toList();
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }
}
