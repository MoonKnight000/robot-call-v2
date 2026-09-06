package uz.murodjon.robotcallv2.scenario.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioFilter;
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

    private final ScenarioRepository repository;
    private final CurrentUser currentUser;
    private final UserService users;
    private final AuditService audit;

    public ScenarioService(ScenarioRepository repository, CurrentUser currentUser, UserService users, AuditService audit) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.users = users;
        this.audit = audit;
    }

    @Override
    public ScenarioRow create(long companyId, CreateScenarioRequest r) {
        requireValid(r.definition());
        String key = keyOf(r.scenarioKey(), r.name());
        if (repository.existsByKey(key)) {
            throw new ConflictException(ErrorCode.SCENARIO_KEY_EXISTS, key);
        }
        long id = repository.create(companyId, key, r.name(), r.description(), false, r.definition(),
                currentUser.id().orElse(null));
        audit.record(companyId, "SCENARIO_CREATE", "scenario", String.valueOf(id), key);
        return scenarioRow(companyId, id);
    }

    @Override
    @Transactional
    public ScenarioRow update(long companyId, long id, UpdateScenarioRequest r) {
        Scenario current = requireScenario(companyId, id);
        if (current.builtin()) {
            throw new ForbiddenException(ErrorCode.SCENARIO_BUILTIN_READONLY, current.scenarioKey());
        }
        requireValid(r.definition());
        int nextVersion = repository.maxVersion(current.scenarioKey()) + 1;
        repository.deactivate(current.scenarioKey());
        long newId = repository.insertVersion(companyId, current.scenarioKey(), nextVersion, r.name(),
                r.description(), false, r.definition(), currentUser.id().orElse(null));
        audit.record(companyId, "SCENARIO_UPDATE", "scenario", String.valueOf(newId),
                current.scenarioKey() + " v" + nextVersion);
        return scenarioRow(companyId, newId);
    }

    @Override
    public ScenarioRow clone(long companyId, long id, CloneScenarioRequest r) {
        Scenario source = requireScenario(companyId, id);
        String key = keyOf(r.scenarioKey(), r.name());
        if (repository.existsByKey(key)) {
            throw new ConflictException(ErrorCode.SCENARIO_KEY_EXISTS, key);
        }
        long newId = repository.create(companyId, key, r.name(), source.description(), false,
                source.definition(), currentUser.id().orElse(null));
        audit.record(companyId, "SCENARIO_CLONE", "scenario", String.valueOf(newId),
                "from " + source.scenarioKey() + " v" + source.version());
        return scenarioRow(companyId, newId);
    }

    @Override
    public ScenarioValidationResult validate(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        return new ScenarioValidationResult(errors.isEmpty(), errors);
    }

    @Override
    public PageableData<ScenarioRow> list(long companyId, ScenarioFilter filter) {
        List<Scenario> rows = repository.findAll(companyId, filter);
        long total = repository.count(companyId, filter);
        return PageableData.of(toRows(companyId, rows), filter.pageOrDefault(), filter.sizeOrDefault(), total);
    }

    private List<ScenarioRow> toRows(long companyId, List<Scenario> rows) {
        Map<Long, String> creatorNames = users.namesByIds(companyId,
                rows.stream().map(Scenario::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(s -> ScenarioRow.of(s, s.createdBy() != null ? creatorNames.get(s.createdBy()) : null))
                .toList();
    }

    @Override
    public ScenarioRow scenarioRow(long companyId, long id) {
        Scenario s = requireScenario(companyId, id);
        String createdByName = s.createdBy() != null
                ? users.namesByIds(companyId, List.of(s.createdBy())).get(s.createdBy())
                : null;
        return ScenarioRow.of(s, createdByName);
    }

    @Override
    public Scenario requireScenario(long companyId, long id) {
        Scenario row = repository.find(companyId, id);
        if (row == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, id);
        }
        return row;
    }

    @Override
    public Scenario findById(long companyId, long id) {
        return repository.find(companyId, id);
    }

    @Override
    public Scenario requireScenarioByKey(String scenarioKey) {
        Scenario row = repository.findActiveByKey(scenarioKey);
        if (row == null) {
            throw new NotFoundException(ErrorCode.SCENARIO_NOT_FOUND, scenarioKey);
        }
        return row;
    }

    @Override
    public List<Scenario> findAllActiveForWarmup() {
        return repository.findAllActive();
    }

    @Override
    public Map<Long, String> scenarioNamesByIds(long companyId, Collection<Long> ids) {
        return repository.namesByIds(companyId, ids);
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
