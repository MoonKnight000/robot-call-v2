package uz.murodjon.uysotvoice.inbound.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.inbound.dto.InboundRouteFilter;
import uz.murodjon.uysotvoice.inbound.dto.InboundRoute;
import uz.murodjon.uysotvoice.inbound.entity.InboundRouteEntity;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

/** JPA-backed DAO for {@code inbound_route} (ROADMAP C.1). */
@Repository
public class InboundRouteRepository {

    private final InboundRouteJpaRepository jpa;
    private final CurrentCompany company;

    public InboundRouteRepository(InboundRouteJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    public long create(String didNumber, long scenarioId, String language, LocalTime businessHoursStart,
                       LocalTime businessHoursEnd, String fallbackMessage) {
        InboundRouteEntity entity = new InboundRouteEntity();
        entity.setCompanyId(company.id());
        entity.setDidNumber(didNumber);
        entity.setScenarioId(scenarioId);
        entity.setLanguage(language != null && !language.isBlank() ? language : "uz-UZ");
        entity.setBusinessHoursStart(businessHoursStart);
        entity.setBusinessHoursEnd(businessHoursEnd);
        entity.setFallbackMessage(fallbackMessage);
        entity.setEnabled(true);
        entity.setCreatedAt(Instant.now());
        return jpa.save(entity).getId();
    }

    /** Scoped to the current company — another company's id reads as missing, not found. */
    public InboundRoute find(long id) {
        return jpa.findByIdAndCompanyId(id, company.id()).map(InboundRouteRepository::toRow).orElse(null);
    }

    public boolean existsEnabledByDid(String didNumber) {
        return jpa.existsByDidNumberAndEnabledTrue(didNumber);
    }

    public boolean existsEnabledByDidExcluding(String didNumber, long id) {
        return jpa.existsByDidNumberAndEnabledTrueAndIdNot(didNumber, id);
    }

    /** No-op if {@code id} does not belong to the current company. */
    public void update(long id, String didNumber, long scenarioId, String language, LocalTime businessHoursStart,
                       LocalTime businessHoursEnd, String fallbackMessage, boolean enabled) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setDidNumber(didNumber);
            entity.setScenarioId(scenarioId);
            entity.setLanguage(language != null && !language.isBlank() ? language : "uz-UZ");
            entity.setBusinessHoursStart(businessHoursStart);
            entity.setBusinessHoursEnd(businessHoursEnd);
            entity.setFallbackMessage(fallbackMessage);
            entity.setEnabled(enabled);
            jpa.save(entity);
        });
    }

    /** No-op if {@code id} does not belong to the current company. */
    public void disable(long id) {
        jpa.findByIdAndCompanyId(id, company.id()).ifPresent(entity -> {
            entity.setEnabled(false);
            jpa.save(entity);
        });
    }

    public List<InboundRoute> findAll(InboundRouteFilter filter) {
        return jpa.findByCompanyId(company.id(), filter.pageable()).stream()
                .map(InboundRouteRepository::toRow)
                .toList();
    }

    public long count(InboundRouteFilter filter) {
        return jpa.countByCompanyId(company.id());
    }

    /**
     * The runtime lookup at {@code StasisStart} (ROADMAP C.1) — unscoped by company, see
     * {@link InboundRouteJpaRepository#findByDidNumberAndEnabledTrue}.
     */
    public InboundRoute resolveByDid(String didNumber) {
        if (didNumber == null || didNumber.isBlank()) {
            return null;
        }
        return jpa.findByDidNumberAndEnabledTrue(didNumber).map(InboundRouteRepository::toRow).orElse(null);
    }

    private static InboundRoute toRow(InboundRouteEntity e) {
        return new InboundRoute(e.getId(), e.getDidNumber(), e.getScenarioId(), e.getLanguage(),
                e.getBusinessHoursStart(), e.getBusinessHoursEnd(), e.getFallbackMessage(), e.isEnabled(),
                e.getCreatedAt());
    }
}
