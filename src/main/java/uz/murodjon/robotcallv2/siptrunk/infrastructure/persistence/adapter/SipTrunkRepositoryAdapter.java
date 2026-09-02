package uz.murodjon.robotcallv2.siptrunk.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkFilter;
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

    private final SipTrunkJpaRepository jpa;
    private final CurrentCompany company;
    private final SipTrunkMapper mapper;

    public SipTrunkRepositoryAdapter(SipTrunkJpaRepository jpa, CurrentCompany company, SipTrunkMapper mapper) {
        this.jpa = jpa;
        this.company = company;
        this.mapper = mapper;
    }

    @Override
    public long create(String name, String pjsipEndpoint, String callerId, boolean makeDefault,
                       String host, int port, String sipUsername, String sipPasswordEnc,
                       SipTrunkTransport transport, List<String> codecs) {
        long companyId = company.id();
        boolean first = !jpa.existsByCompanyId(companyId);
        boolean asDefault = makeDefault || first;
        if (asDefault) {
            jpa.clearDefault(companyId);
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
        return jpa.save(entity).getId();
    }

    @Override
    public SipTrunk find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public boolean hasDefault() {
        return jpa.existsByCompanyIdAndIsDefaultTrue(company.id());
    }

    @Override
    public void update(long id, String name, String pjsipEndpoint, String callerId, boolean enabled,
                       String host, int port, String sipUsername, String sipPasswordEnc,
                       SipTrunkTransport transport, List<String> codecs) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
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
            jpa.save(entity);
        });
    }

    @Override
    public void updatePjsipEndpoint(long id, String pjsipEndpoint) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setPjsipEndpoint(pjsipEndpoint);
            jpa.save(entity);
        });
    }

    @Override
    @Transactional
    public void makeDefault(long id) {
        long companyId = company.id();
        jpa.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            jpa.clearDefault(companyId);
            entity.setDefault(true);
            jpa.save(entity);
        });
    }

    @Override
    public void delete(long id) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(jpa::delete);
    }

    @Override
    public List<SipTrunk> findAll(SipTrunkFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public long count(SipTrunkFilter filter) {
        return jpa.countByCompanyId(company.id());
    }

    @Override
    public SipTrunk findDefaultForCompany(long companyId) {
        return jpa.findByCompanyIdAndIsDefaultTrueAndEnabledTrue(companyId)
                .map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public List<SipTrunk> findAllEnabledByCompany(long companyId) {
        return jpa.findByCompanyIdAndEnabledTrue(companyId).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public List<SipTrunk> findEnabledByIdsAndCompany(Collection<Long> ids, long companyId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return jpa.findByIdInAndCompanyIdAndEnabledTrue(ids, companyId).stream()
                .map(mapper::entityToDomain)
                .toList();
    }

    @Override
    public List<SipTrunk> findAllManagedEnabled() {
        return jpa.findByHostIsNotNullAndEnabledTrue().stream().map(mapper::entityToDomain).toList();
    }
}
