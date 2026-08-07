package uz.murodjon.uysotvoice.scenario.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.scenario.dto.CloneScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.CreateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioFilter;
import uz.murodjon.uysotvoice.scenario.dto.Scenario;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioRow;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioValidationResult;
import uz.murodjon.uysotvoice.scenario.dto.UpdateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.repository.ScenarioRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ErrorCode;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;
import uz.murodjon.uysotvoice.user.service.CurrentUser;
import uz.murodjon.uysotvoice.user.service.UserService;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Scenario CRUD, versioning and validation (ROADMAP A.4). Storage only in this
 * pass — see {@link ScenarioDefinition} for what deliberately stays out of scope.
 */
@Service
public class ScenarioService {

    private final ScenarioRepository repo;
    private final CurrentUser currentUser;
    private final UserService users;
    private final AuditService audit;

    public ScenarioService(ScenarioRepository repo, CurrentUser currentUser, UserService users, AuditService audit) {
        this.repo = repo;
        this.currentUser = currentUser;
        this.users = users;
        this.audit = audit;
    }

    public ScenarioRow create(CreateScenarioRequest r) {
        requireValid(r.definition());
        String key = keyOf(r.scenarioKey(), r.name());
        if (repo.existsByKey(key)) {
            throw new ConflictException(ErrorCode.SCENARIO_KEY_EXISTS, key);
        }
        long id = repo.create(key, r.name(), r.description(), false, r.definition(), currentUser.id().orElse(null));
        audit.record("SCENARIO_CREATE", "scenario", String.valueOf(id), key);
        return scenarioRow(id);
    }

    /**
     * Creates a new version of {@code id}'s scenario — the row {@code id} points at is never
     * mutated. Transactional: {@code deactivate} and {@code insertVersion} must succeed
     * together, or a failed insert would leave {@code scenarioKey} with no active version.
     */
    @Transactional
    public ScenarioRow update(long id, UpdateScenarioRequest r) {
        Scenario current = requireScenario(id);
        if (current.builtin()) {
            throw new ForbiddenException(ErrorCode.SCENARIO_BUILTIN_READONLY, current.scenarioKey());
        }
        requireValid(r.definition());
        int nextVersion = repo.maxVersion(current.scenarioKey()) + 1;
        repo.deactivate(current.scenarioKey());
        long newId = repo.insertVersion(current.scenarioKey(), nextVersion, r.name(), r.description(),
                false, r.definition(), currentUser.id().orElse(null));
        audit.record("SCENARIO_UPDATE", "scenario", String.valueOf(newId),
                current.scenarioKey() + " v" + nextVersion);
        return scenarioRow(newId);
    }

    /** Copies {@code id} (built-in or custom) into a brand new, editable scenario key. */
    public ScenarioRow clone(long id, CloneScenarioRequest r) {
        Scenario source = requireScenario(id);
        String key = keyOf(r.scenarioKey(), r.name());
        if (repo.existsByKey(key)) {
            throw new ConflictException(ErrorCode.SCENARIO_KEY_EXISTS, key);
        }
        long newId = repo.create(key, r.name(), source.description(), false, source.definition(),
                currentUser.id().orElse(null));
        audit.record("SCENARIO_CLONE", "scenario", String.valueOf(newId),
                "from " + source.scenarioKey() + " v" + source.version());
        return scenarioRow(newId);
    }

    public ScenarioValidationResult validate(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        return new ScenarioValidationResult(errors.isEmpty(), errors);
    }

    /**
     * API-facing list (§10.7) — enriches each row with {@code createdByName} via a
     * batched lookup rather than one query per row, matching {@code CampaignService
     * #listCampaigns}.
     */
    public PageableData<ScenarioRow> list(ScenarioFilter filter) {
        List<Scenario> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(toRows(rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** Batched {@code createdByName} enrichment for a page of scenarios. */
    private List<ScenarioRow> toRows(List<Scenario> rows) {
        Map<Long, String> creatorNames = users.namesByIds(
                rows.stream().map(Scenario::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(s -> ScenarioRow.of(s, s.createdBy() != null ? creatorNames.get(s.createdBy()) : null))
                .toList();
    }

    /** As {@link #list}, for a single scenario — used by {@code create}/{@code get}/{@code update}/{@code clone}. */
    public ScenarioRow scenarioRow(long id) {
        Scenario s = requireScenario(id);
        String createdByName = s.createdBy() != null
                ? users.namesByIds(List.of(s.createdBy())).get(s.createdBy())
                : null;
        return ScenarioRow.of(s, createdByName);
    }

    /** As {@link ScenarioRepository#find}, for the REST API — a missing scenario is a 404, not a null. */
    public Scenario requireScenario(long id) {
        Scenario row = repo.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, id);
        }
        return row;
    }

    /** The active row for {@code scenarioKey} (e.g. a manual test call's default, ROADMAP A.3). */
    public Scenario requireScenarioByKey(String scenarioKey) {
        Scenario row = repo.findActiveByKey(scenarioKey);
        if (row == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, scenarioKey);
        }
        return row;
    }

    /**
     * Every active scenario across companies, for the TTS warm-up: a scenario that words
     * the §11.1 disclosure itself makes that text the first thing a caller hears, so it
     * belongs in the cache before the call arrives rather than being synthesized while
     * the line is already open. Unscoped — the warm-up runs at startup, outside any
     * request, and covers every tenant this instance serves.
     */
    public List<Scenario> findAllActiveForWarmup() {
        return repo.findAllActive();
    }

    /**
     * Cheap id→name lookup for other features to enrich their own rows with a
     * {@code scenarioName} (e.g. {@code CampaignRow}, {@code InboundRoute}) — see
     * {@link ScenarioRepository#namesByIds}.
     */
    public Map<Long, String> scenarioNamesByIds(Collection<Long> ids) {
        return repo.namesByIds(ids);
    }

    private static void requireValid(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.SCENARIO_DEFINITION_INVALID, String.join("; ", errors));
        }
    }

    /** A blank key is derived from the name: lowercase, non-alphanumerics collapsed to a hyphen. */
    private static String keyOf(String key, String name) {
        if (key != null && !key.isBlank()) {
            return key.trim();
        }
        String slug = name.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            throw new ValidationException(ErrorCode.SCENARIO_KEY_DERIVE_FAILED, name);
        }
        return slug;
    }
}
