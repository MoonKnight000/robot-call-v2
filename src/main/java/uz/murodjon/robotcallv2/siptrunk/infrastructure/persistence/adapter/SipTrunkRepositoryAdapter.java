package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunkFilter;
import uz.murodjon.robotcallv2.siptrunk.application.mapper.SipTrunkMapper;
import uz.murodjon.robotcallv2.siptrunk.application.port.output.SipTrunkRepository;
import uz.murodjon.robotcallv2.siptrunk.domain.entity.SipTrunk;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.entity.SipTrunkEntity;
import uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.repository.SipTrunkJpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Component
public class SipTrunkRepositoryAdapter implements SipTrunkRepository {

    private final SipTrunkJpaRepository jpaRepository;
    private final SipTrunkMapper mapper;

    public SipTrunkRepositoryAdapter(SipTrunkJpaRepository jpaRepository, SipTrunkMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, String name, String pjsipEndpoint, String callerId, boolean makeDefault,
                       String host, int port, String sipUsername, String sipPasswordEnc,
                       SipTrunkTransport transport, List<String> codecs) {
        boolean first = !jpaRepository.existsByCompanyId(companyId);
        boolean asDefault = makeDefault || first;
        if (asDefault) {
            jpaRepository.clearDefault(companyId);
        }
        SipTrunkEntity entity = new SipTrunkEntity();
        entity.setCompanyId(companyId);
        entity.setName(name);
        entity.setPjsipEndpoint(pjsipEndpoint);
        entity.setCallerId(callerId);
        entity.setHost(host);
        entity.setPort(port);
        entity.setSipUsername(sipUsername);
        entity.setSipPasswordEnc(sipPasswordEnc);
        entity.setTransport(transport);
        entity.setCodecs(codecs);
        entity.setDefault(asDefault);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpaRepository.save(entity).getId();
    }

    @Override
    public SipTrunk find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public boolean hasDefault(long companyId) {
        return jpaRepository.existsByCompanyIdAndIsDefaultTrue(companyId);
    }

    @Override
    public void update(long companyId, long id, String name, String pjsipEndpoint, String callerId, boolean enabled,
                       String host, int port, String sipUsername, String sipPasswordEnc,
                       SipTrunkTransport transport, List<String> codecs) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setName(name);
            entity.setPjsipEndpoint(pjsipEndpoint);
            entity.setCallerId(callerId);
            entity.setEnabled(enabled);
            entity.setHost(host);
            entity.setPort(port);
            entity.setSipUsername(sipUsername);
            if (sipPasswordEnc != null) {
                entity.setSipPasswordEnc(sipPasswordEnc);
            }
            entity.setTransport(transport);
            entity.setCodecs(codecs);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void updatePjsipEndpoint(long companyId, long id, String pjsipEndpoint) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            entity.setPjsipEndpoint(pjsipEndpoint);
            jpaRepository.save(entity);
        });
    }

    @Override
    @Transactional
    public void makeDefault(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            jpaRepository.clearDefault(companyId);
            entity.setDefault(true);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public List<SipTrunk> findAll(long companyId, SipTrunkFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(long companyId, SipTrunkFilter filter) {
        return jpaRepository.countByCompanyId(companyId);
    }

    @Override
    public SipTrunk findDefaultForCompany(long companyId) {
        return jpaRepository.findByCompanyIdAndIsDefaultTrueAndEnabledTrue(companyId)
                .map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<SipTrunk> findAllEnabledByCompany(long companyId) {
        return jpaRepository.findByCompanyIdAndEnabledTrue(companyId).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public List<SipTrunk> findEnabledByIdsAndCompany(Collection<Long> ids, long companyId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findByIdInAndCompanyIdAndEnabledTrue(ids, companyId).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public List<SipTrunk> findAllManagedEnabled() {
        return jpaRepository.findByHostIsNotNullAndEnabledTrue().stream().map(mapper::entityToDomain).toList();
    }
}
