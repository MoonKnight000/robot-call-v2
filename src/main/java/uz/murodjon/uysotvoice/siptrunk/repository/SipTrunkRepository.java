package uz.murodjon.uysotvoice.siptrunk.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkFilter;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunk;
import uz.murodjon.uysotvoice.siptrunk.entity.SipTrunkEntity;

import java.time.Instant;
import java.util.List;

/** JPA-backed DAO for {@code sip_trunk} (ROADMAP B.3). */
@Repository
public class SipTrunkRepository {

    private final SipTrunkJpaRepository jpa;
    private final CurrentCompany company;

    public SipTrunkRepository(SipTrunkJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    /** {@code makeDefault} is forced true for a company's first trunk — never leave it unroutable. */
    public long create(String name, String pjsipEndpoint, String callerId, boolean makeDefault) {
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
        entity.setDefault(asDefault);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company — another company's id reads as missing, not found. */
    public SipTrunk find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(SipTrunkRepository::toRow).orElse(null);
    }

    public boolean hasDefault() {
        return jpa.existsByCompanyIdAndIsDefaultTrue(company.id());
    }

    /** No-op if {@code id} does not belong to the current company. Does not touch {@code isDefault}. */
    public void update(long id, String name, String pjsipEndpoint, String callerId, boolean enabled) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setName(name);
            entity.setPjsipEndpoint(pjsipEndpoint);
            entity.setCallerId(callerId);
            entity.setEnabled(enabled);
            jpa.save(entity);
        });
    }

    /** No-op if {@code id} does not belong to the current company. */
    @Transactional
    public void makeDefault(long id) {
        long companyId = company.id();
        jpa.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            jpa.clearDefault(companyId);
            entity.setDefault(true);
            jpa.save(entity);
        });
    }

    /** No-op if {@code id} does not belong to the current company. */
    public void delete(long id) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(jpa::delete);
    }

    public List<SipTrunk> findAll(SipTrunkFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(SipTrunkRepository::toRow)
                .toList();
    }

    public long count(SipTrunkFilter filter) {
        return jpa.countByCompanyId(company.id());
    }

    /**
     * The runtime lookup at call-origination time (ROADMAP B.3) — unscoped by {@code
     * CurrentCompany}, see {@link SipTrunkJpaRepository#findByCompanyIdAndIsDefaultTrueAndEnabledTrue}.
     * Never throws: a company with no enabled default trunk simply has no trunk here,
     * and the caller falls back to the globally configured one.
     */
    public SipTrunk findDefaultForCompany(long companyId) {
        return jpa.findByCompanyIdAndIsDefaultTrueAndEnabledTrue(companyId)
                .map(SipTrunkRepository::toRow).orElse(null);
    }

    private static SipTrunk toRow(SipTrunkEntity e) {
        return new SipTrunk(e.getId(), e.getName(), e.getPjsipEndpoint(), e.getCallerId(),
                e.isDefault(), e.isEnabled(), e.getCreatedAt());
    }
}
