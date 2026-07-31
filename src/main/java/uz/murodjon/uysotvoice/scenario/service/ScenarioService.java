package uz.murodjon.uysotvoice.scenario.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.scenario.dto.CloneScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.CreateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioDefinition;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioFilter;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioRow;
import uz.murodjon.uysotvoice.scenario.dto.ScenarioValidationResult;
import uz.murodjon.uysotvoice.scenario.dto.UpdateScenarioRequest;
import uz.murodjon.uysotvoice.scenario.repository.ScenarioRepository;
import uz.murodjon.uysotvoice.shared.api.PageableData;
import uz.murodjon.uysotvoice.shared.exception.ConflictException;
import uz.murodjon.uysotvoice.shared.exception.ForbiddenException;
import uz.murodjon.uysotvoice.shared.exception.NotFoundException;
import uz.murodjon.uysotvoice.shared.exception.ValidationException;

import java.util.List;
import java.util.Locale;

/**
 * Scenario CRUD, versioning and validation (ROADMAP A.4). Storage only in this
 * pass — see {@link ScenarioDefinition} for what deliberately stays out of scope.
 */
@Service
public class ScenarioService {

    private final ScenarioRepository repo;
    private final AuditService audit;

    public ScenarioService(ScenarioRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    public ScenarioRow create(CreateScenarioRequest r) {
        requireValid(r.definition());
        String key = keyOf(r.scenarioKey(), r.name());
        if (repo.existsByKey(key)) {
            throw new ConflictException("Scenario key '" + key + "' already exists");
        }
        long id = repo.create(key, r.name(), r.description(), false, r.definition(), null);
        audit.record("SCENARIO_CREATE", "scenario", String.valueOf(id), key);
        return repo.find(id);
    }

    /**
     * Creates a new version of {@code id}'s scenario — the row {@code id} points at is never
     * mutated. Transactional: {@code deactivate} and {@code insertVersion} must succeed
     * together, or a failed insert would leave {@code scenarioKey} with no active version.
     */
    @Transactional
    public ScenarioRow update(long id, UpdateScenarioRequest r) {
        ScenarioRow current = requireScenario(id);
        if (current.builtin()) {
            throw new ForbiddenException(
                    "Built-in scenario '" + current.scenarioKey() + "' cannot be edited — clone it first");
        }
        requireValid(r.definition());
        int nextVersion = repo.maxVersion(current.scenarioKey()) + 1;
        repo.deactivate(current.scenarioKey());
        long newId = repo.insertVersion(current.scenarioKey(), nextVersion, r.name(), r.description(),
                false, r.definition(), null);
        audit.record("SCENARIO_UPDATE", "scenario", String.valueOf(newId),
                current.scenarioKey() + " v" + nextVersion);
        return repo.find(newId);
    }

    /** Copies {@code id} (built-in or custom) into a brand new, editable scenario key. */
    public ScenarioRow clone(long id, CloneScenarioRequest r) {
        ScenarioRow source = requireScenario(id);
        String key = keyOf(r.scenarioKey(), r.name());
        if (repo.existsByKey(key)) {
            throw new ConflictException("Scenario key '" + key + "' already exists");
        }
        long newId = repo.create(key, r.name(), source.description(), false, source.definition(), null);
        audit.record("SCENARIO_CLONE", "scenario", String.valueOf(newId),
                "from " + source.scenarioKey() + " v" + source.version());
        return repo.find(newId);
    }

    public ScenarioValidationResult validate(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        return new ScenarioValidationResult(errors.isEmpty(), errors);
    }

    public PageableData<ScenarioRow> list(ScenarioFilter filter) {
        List<ScenarioRow> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(rows, filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    /** As {@link ScenarioRepository#find}, for the REST API — a missing scenario is a 404, not a null. */
    public ScenarioRow requireScenario(long id) {
        ScenarioRow row = repo.find(id);
        if (row == null) {
            throw new NotFoundException("scenario", id);
        }
        return row;
    }

    private static void requireValid(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        if (!errors.isEmpty()) {
            throw new ValidationException("Invalid scenario definition: " + String.join("; ", errors));
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
            throw new ValidationException("Could not derive a scenario key from name '" + name + "'");
        }
        return slug;
    }
}
