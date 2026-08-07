package uz.murodjon.uysotvoice.siptrunk.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.siptrunk.domain.SipTrunk;
import uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkFilter;
import uz.murodjon.uysotvoice.siptrunk.entity.SipTrunkEntity;
import uz.murodjon.uysotvoice.siptrunk.enums.SipTrunkTransport;

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

    /**
     * {@code makeDefault} is forced true for a company's first trunk — never leave it
     * unroutable. {@code pjsipEndpoint} is the final manual-mode name, or a placeholder
     * for a managed one — the caller (service layer) knows the mode and, for managed,
     * follows up with {@link #updatePjsipEndpoint} once the generated id is known.
     */
    public long create(String name, String pjsipEndpoint, String callerId, boolean makeDefault,
                       String host, int port, String sipUsername, String sipPasswordEnc,
                       SipTrunkTransport transport) {
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
        entity.setDefault(asDefault);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company — another company's id reads as missing, not found. */
    public SipTrunk find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(SipTrunkRepository::toSipTrunk).orElse(null);
    }

    public boolean hasDefault() {
        return jpa.existsByCompanyIdAndIsDefaultTrue(company.id());
    }

    /**
     * No-op if {@code id} does not belong to the current company. Does not touch {@code
     * isDefault}. {@code sipPasswordEnc} of {@code null} keeps the trunk's current
     * encrypted password — see {@code SipTrunkService#update}.
     */
    public void update(long id, String name, String pjsipEndpoint, String callerId, boolean enabled,
                       String host, int port, String sipUsername, String sipPasswordEnc,
                       SipTrunkTransport transport) {
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
            jpa.save(entity);
        });
    }

    /**
     * {@code POST /api/sip-trunks} for a managed trunk (report #7) — the generated
     * endpoint name embeds {@code id}, which only exists after {@link #create}'s insert.
     */
    public void updatePjsipEndpoint(long id, String pjsipEndpoint) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setPjsipEndpoint(pjsipEndpoint);
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
                .map(SipTrunkRepository::toSipTrunk)
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
                .map(SipTrunkRepository::toSipTrunk).orElse(null);
    }

    /**
     * Every enabled managed-mode trunk, across every company, with its encrypted
     * password intact — {@code siptrunk.service.PjsipConfigWriter} only, never exposed
     * through {@link uz.murodjon.uysotvoice.siptrunk.dto.SipTrunkRow}/the API.
     */
    public List<SipTrunk> findAllManagedEnabled() {
        return jpa.findByHostIsNotNullAndEnabledTrue().stream().map(SipTrunkRepository::toSipTrunk).toList();
    }

    private static SipTrunk toSipTrunk(SipTrunkEntity e) {
        return new SipTrunk(e.getId(), e.getCompanyId(), e.getName(), e.getPjsipEndpoint(), e.getCallerId(),
                e.getHost(), e.getPort(), e.getSipUsername(), e.getSipPasswordEnc(), e.getTransport(),
                e.isDefault(), e.isEnabled(), e.getCreatedAt());
    }
}
