package uz.murodjon.robotcallv2.callflow.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callflow.infrastructure.persistence.mapper.CallFlowEntityMapper;
import uz.murodjon.robotcallv2.callflow.application.port.output.CallFlowRepository;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlow;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowFilter;
import uz.murodjon.robotcallv2.callflow.domain.entity.CallFlowGraph;
import uz.murodjon.robotcallv2.callflow.infrastructure.persistence.entity.CallFlowEntity;
import uz.murodjon.robotcallv2.callflow.infrastructure.persistence.repository.CallFlowJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class CallFlowRepositoryAdapter implements CallFlowRepository {

    private final CallFlowJpaRepository jpaRepository;
    private final CallFlowEntityMapper mapper;

    public CallFlowRepositoryAdapter(CallFlowJpaRepository jpaRepository, CallFlowEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, String name, String description, CallFlowGraph draft) {
        CallFlowEntity entity = new CallFlowEntity();
        entity.setCompanyId(companyId);
        entity.setName(name);
        entity.setDescription(description);
        entity.setDraftGraph(mapper.writeGraph(draft));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void updateDraft(long companyId, long id, String name, String description, CallFlowGraph draft) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setName(name);
            entity.setDescription(description);
            entity.setDraftGraph(mapper.writeGraph(draft));
            entity.setUpdatedAt(Instant.now());
            jpaRepository.save(entity);
        });
    }

    @Override
    public void publish(long companyId, long id, CallFlowGraph graph, Instant at) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setPublishedGraph(mapper.writeGraph(graph));
            entity.setVersion(entity.getVersion() + 1);
            entity.setPublishedAt(at);
            entity.setUpdatedAt(at);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public CallFlow find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toCallFlow).orElse(null);
    }

    @Override
    public List<CallFlow> findAll(long companyId, CallFlowFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream().map(mapper::toCallFlow).toList();
    }

    @Override
    public long count(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }
}
