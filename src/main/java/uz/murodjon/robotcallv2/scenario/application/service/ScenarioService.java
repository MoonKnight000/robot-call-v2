package uz.murodjon.robotcallv2.scenario.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioValidationResult;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioValidator;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.application.service.UserService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scenario CRUD, versioning and validation (ROADMAP A.4).
 */
@Service
public class ScenarioService implements ScenarioUseCase {

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

    @Override
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

    @Override
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

    @Override
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

    @Override
    public ScenarioValidationResult validate(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        return new ScenarioValidationResult(errors.isEmpty(), errors);
    }

    @Override
    public PageableData<ScenarioRow> list(ScenarioFilter filter) {
        List<Scenario> rows = repo.findAll(filter);
        long total = repo.count(filter);
        return PageableData.of(toRows(rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    private List<ScenarioRow> toRows(List<Scenario> rows) {
        Map<Long, String> creatorNames = users.namesByIds(
                rows.stream().map(Scenario::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(s -> ScenarioRow.of(s, s.createdBy() != null ? creatorNames.get(s.createdBy()) : null))
                .toList();
    }

    @Override
    public ScenarioRow scenarioRow(long id) {
        Scenario s = requireScenario(id);
        String createdByName = s.createdBy() != null
                ? users.namesByIds(List.of(s.createdBy())).get(s.createdBy())
                : null;
        return ScenarioRow.of(s, createdByName);
    }

    @Override
    public Scenario requireScenario(long id) {
        Scenario row = repo.find(id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public Scenario findById(long id) {
        return repo.find(id);
    }

    @Override
    public Scenario requireScenarioByKey(String scenarioKey) {
        Scenario row = repo.findActiveByKey(scenarioKey);
        if (row == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, scenarioKey);
        }
        return row;
    }

    @Override
    public List<Scenario> findAllActiveForWarmup() {
        return repo.findAllActive();
    }

    @Override
    public Map<Long, String> scenarioNamesByIds(Collection<Long> ids) {
        return repo.namesByIds(ids);
    }

    private static void requireValid(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        if (!errors.isEmpty()) {
            throw new ValidationException(ErrorCode.SCENARIO_DEFINITION_INVALID, String.join("; ", errors));
        }
    }

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
