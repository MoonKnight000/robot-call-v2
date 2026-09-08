package uz.murodjon.robotcallv2.memory.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.memory.application.mapper.ClientMemoryMapper;
import uz.murodjon.robotcallv2.memory.application.port.output.ClientMemoryRepository;
import uz.murodjon.robotcallv2.memory.domain.entity.ClientMemory;
import uz.murodjon.robotcallv2.memory.infrastructure.persistence.entity.ClientMemoryEntity;
import uz.murodjon.robotcallv2.memory.infrastructure.persistence.repository.ClientMemoryJpaRepository;

import java.time.Instant;

@Component
public class ClientMemoryRepositoryAdapter implements ClientMemoryRepository {

    private final ClientMemoryJpaRepository jpaRepository;
    private final ClientMemoryMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public ClientMemoryRepositoryAdapter(ClientMemoryJpaRepository jpaRepository, ClientMemoryMapper mapper,
                                         CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public ClientMemory findByCompanyIdAndPhone(long companyId, String phone) {
        return jpaRepository.findByCompanyIdAndPhone(companyId, phone)
                .map(mapper::toClientMemory)
                .orElse(null);
    }

    @Override
    public ClientMemory upsert(long companyId, ClientMemory memory) {
        ClientMemoryEntity entity = jpaRepository.findByCompanyIdAndPhone(companyId, memory.phone()).orElseGet(() -> {
            ClientMemoryEntity fresh = new ClientMemoryEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        mapper.applyToEntity(memory, entity);
        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(Instant.now());
        }
        return mapper.toClientMemory(jpaRepository.save(entity));
    }
}
