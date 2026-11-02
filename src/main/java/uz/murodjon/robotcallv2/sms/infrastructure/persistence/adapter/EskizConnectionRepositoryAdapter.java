package uz.murodjon.robotcallv2.sms.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.sms.application.port.output.EskizConnectionRepository;
import uz.murodjon.robotcallv2.sms.domain.entity.EskizConnection;
import uz.murodjon.robotcallv2.sms.infrastructure.persistence.entity.EskizConnectionEntity;
import uz.murodjon.robotcallv2.sms.infrastructure.persistence.mapper.EskizConnectionEntityMapper;
import uz.murodjon.robotcallv2.sms.infrastructure.persistence.repository.EskizConnectionJpaRepository;

import java.util.Optional;

@Component
public class EskizConnectionRepositoryAdapter implements EskizConnectionRepository {

    private final EskizConnectionJpaRepository jpaRepository;
    private final EskizConnectionEntityMapper mapper;

    public EskizConnectionRepositoryAdapter(EskizConnectionJpaRepository jpaRepository,
                                            EskizConnectionEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Optional<EskizConnection> findByCompanyId(long companyId) {
        return jpaRepository.findById(companyId).map(mapper::toEskizConnection);
    }

    @Override
    public EskizConnection upsert(long companyId, EskizConnection connection) {
        EskizConnectionEntity entity = jpaRepository.findById(companyId).orElseGet(EskizConnectionEntity::new);
        mapper.applyEskizConnection(entity, companyId, connection);
        return mapper.toEskizConnection(jpaRepository.save(entity));
    }

    @Override
    public void deleteByCompanyId(long companyId) {
        if (jpaRepository.existsById(companyId)) {
            jpaRepository.deleteById(companyId);
        }
    }
}
