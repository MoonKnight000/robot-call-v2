package uz.murodjon.uysotvoice.scenario.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioFilter;
import uz.murodjon.uysotvoice.scenario.dto.Scenario;
import uz.murodjon.uysotvoice.scenario.entity.ScenarioEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * JPA-backed DAO for {@code scenario} (ROADMAP A.4). {@code company_id} is nullable:
 * {@code NULL} means a global built-in template, visible to every company; a non-null
 * value scopes a custom scenario to the company that created it (ROADMAP B.1).
 */
@Repository
public class ScenarioRepository {

    private static final Logger log = LoggerFactory.getLogger(ScenarioRepository.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScenarioJpaRepository jpa;
    private final CurrentCompany company;

    public ScenarioRepository(ScenarioJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    private static ScenarioDefinition readDefinition(String json) {
        try {
            return JSON.readValue(json, ScenarioDefinition.class);
        } catch (Exception e) {
            // A row that fails to parse is a data problem worth surfacing loudly, not a
            // null definition a caller would otherwise NPE on far from this class.
            log.error("Corrupt scenario definition JSON: {}", e.getMessage());
            throw new IllegalStateException("Corrupt scenario definition: " + e.getMessage(), e);
        }
    }

    private static String writeDefinition(ScenarioDefinition def) {
        try {
            return JSON.writeValueAsString(def);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize scenario definition: " + e.getMessage(), e);
        }
    }

    /** Inserts the first version (version 1) of a new scenario key. */
    public long create(String scenarioKey, String name, String description, boolean builtin,
                       ScenarioDefinition definition, Long createdBy) {
        return insertVersion(scenarioKey, 1, name, description, builtin, definition, createdBy);
    }

    /**
     * Inserts a specific version, active by construction — the caller deactivates any
     * prior one. {@code company_id} is {@code null} for a builtin (global) row, or the
     * current company for a custom one.
     */
    public long insertVersion(String scenarioKey, int version, String name, String description,
                              boolean builtin, ScenarioDefinition definition, Long createdBy) {
        ScenarioEntity entity = new ScenarioEntity();
        entity.setScenarioKey(scenarioKey);
        entity.setVersion(version);
        entity.setName(name);
        entity.setDescription(description);
        entity.setBuiltin(builtin);
        entity.setActive(true);
        entity.setDefinition(writeDefinition(definition));
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(Instant.now());
        entity.setCompanyId(builtin ? null : company.id());
        return jpa.save(entity).getId();
    }

    /** Flips the current active version of {@code scenarioKey} off, ahead of inserting the next one. */
    public void deactivate(String scenarioKey) {
        jpa.deactivate(scenarioKey);
    }

    /** Visible if it's a global builtin (null company_id) or belongs to the current company. */
    public Scenario find(long id) {
        return jpa.findVisible(id, company.id()).map(ScenarioRepository::toRow).orElse(null);
    }

    /**
     * Batch id→name lookup for enriching list rows (e.g. {@code CampaignRow},
     * {@code InboundRoute}) with a {@code scenarioName} without parsing every scenario's
     * JSON definition. Missing/invisible ids are simply absent from the result map.
     */
    public Map<Long, String> namesByIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jpa.findNamesByIds(ids, company.id()).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (String) row[1]));
    }

    /** Single-id convenience over {@link #namesByIds}, for detail (non-list) endpoints. */
    public String nameById(long id) {
        return namesByIds(List.of(id)).get(id);
    }

    public int maxVersion(String scenarioKey) {
        Integer max = jpa.maxVersion(scenarioKey);
        return max != null ? max : 0;
    }

    public boolean existsByKey(String scenarioKey) {
        return jpa.existsByScenarioKey(scenarioKey);
    }

    /**
     * The active row for {@code scenarioKey} (e.g. the default test scenario, ROADMAP
     * A.3) — unscoped by company, since a builtin key resolves the same way for anyone.
     */
    public Scenario findActiveByKey(String scenarioKey) {
        return jpa.findByScenarioKeyAndActiveTrue(scenarioKey).map(ScenarioRepository::toRow).orElse(null);
    }

    /**
     * Every scenario's current active version — never a superseded one (§10.7 list).
     * Includes global builtins plus the current company's own custom scenarios.
     */
    public List<Scenario> findAll(ScenarioFilter filter) {
        return jpa.findVisible(company.id(), filter.builtinOnly(), filter.pageable()).stream()
                .map(ScenarioRepository::toRow)
                .toList();
    }

    public long count(ScenarioFilter filter) {
        return jpa.countVisible(company.id(), filter.builtinOnly());
    }

    /**
     * Every active scenario, of every company — see
     * {@code ScenarioService.findAllActiveForWarmup}. Unscoped on purpose, unlike
     * {@link #findAll}: this one runs outside any request, where there is no current
     * company to scope to.
     */
    public List<Scenario> findAllActive() {
        return jpa.findByActiveTrue().stream().map(ScenarioRepository::toRow).toList();
    }

    private static Scenario toRow(ScenarioEntity e) {
        return new Scenario(
                e.getId(),
                e.getScenarioKey(),
                e.getVersion(),
                e.getName(),
                e.getDescription(),
                e.isBuiltin(),
                e.isActive(),
                readDefinition(e.getDefinition()),
                e.getCreatedAt(),
                e.getCreatedBy());
    }
}
