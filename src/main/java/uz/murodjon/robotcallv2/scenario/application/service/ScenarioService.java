package uz.murodjon.robotcallv2.scenario.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.application.port.input.ScenarioUseCase;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioCanvasConverter;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioCanvasSimulator;
import uz.murodjon.robotcallv2.scenario.domain.service.ScenarioValidator;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.*;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Scenario CRUD, versioning, visual canvas studio and validation (ROADMAP A.4).
 */
@Service
public class ScenarioService implements ScenarioUseCase {

    private final ScenarioRepository repository;
    private final CurrentUser currentUser;
    private final UserUseCase userUseCase;
    private final AuditUseCase audit;
    /** Where a stage's own model is checked against the catalog. */
    private final AiModelUseCase aiModelUseCase;

    public ScenarioService(ScenarioRepository repository, CurrentUser currentUser, UserUseCase userUseCase,
                           AuditUseCase audit, AiModelUseCase aiModelUseCase) {
        this.repository = repository;
        this.currentUser = currentUser;
        this.userUseCase = userUseCase;
        this.audit = audit;
        this.aiModelUseCase = aiModelUseCase;
    }

    @Override
    public ScenarioRow create(long companyId, CreateScenarioRequest request) {
        requireValid(request.definition());
        String key = keyOf(request.scenarioKey(), request.name());
        if (repository.existsByKey(key)) {
            throw new ConflictException(ErrorCode.SCENARIO_KEY_EXISTS, key);
        }
        long id = repository.create(companyId, key, request.name(), request.description(), false, request.definition(),
                currentUser.id().orElse(null));
        audit.record(companyId, "SCENARIO_CREATE", "scenario", String.valueOf(id), key);
        return findScenarioRow(companyId, id);
    }

    @Override
    @Transactional
    public ScenarioRow update(long companyId, long id, UpdateScenarioRequest request) {
        Scenario current = requireScenario(companyId, id);
        if (current.builtin()) {
            throw new ForbiddenException(ErrorCode.SCENARIO_BUILTIN_READONLY, current.scenarioKey());
        }
        requireValid(request.definition());
        int latestVersion = repository.findMaxVersion(current.scenarioKey());
        // Rows are copy-on-write, so the scenario's own version is the optimistic lock. Not
        // sent: the latest, so a client that predates it edits as before. Retiring it is
        // conditional, so of two edits racing on one version the second is answered 409
        // rather than failing on uq_scenario_key_version.
        int expectedVersion = request.version() != null ? request.version() : latestVersion;
        if (!repository.deactivate(current.scenarioKey(), expectedVersion)) {
            throw new ConflictException(ErrorCode.ENTITY_VERSION_CONFLICT, "scenario", id);
        }
        int nextVersion = latestVersion + 1;
        long newId = repository.insertVersion(companyId, current.scenarioKey(), nextVersion, request.name(),
                request.description(), false, request.definition(), currentUser.id().orElse(null));
        audit.record(companyId, "SCENARIO_UPDATE", "scenario", String.valueOf(newId),
                current.scenarioKey() + " v" + nextVersion);
        return findScenarioRow(companyId, newId);
    }

    @Override
    public ScenarioRow clone(long companyId, long id, CloneScenarioRequest request) {
        Scenario source = requireScenario(companyId, id);
        String key = keyOf(request.scenarioKey(), request.name());
        if (repository.existsByKey(key)) {
            throw new ConflictException(ErrorCode.SCENARIO_KEY_EXISTS, key);
        }
        long newId = repository.create(companyId, key, request.name(), source.description(), false,
                source.definition(), currentUser.id().orElse(null));
        audit.record(companyId, "SCENARIO_CLONE", "scenario", String.valueOf(newId),
                "from " + source.scenarioKey() + " v" + source.version());
        return findScenarioRow(companyId, newId);
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
        Map<Long, String> creatorNames = userUseCase.findNamesByIds(companyId,
                rows.stream().map(Scenario::createdBy).filter(Objects::nonNull).collect(Collectors.toSet()));
        return rows.stream()
                .map(s -> ScenarioRow.of(s, s.createdBy() != null ? creatorNames.get(s.createdBy()) : null))
                .toList();
    }

    @Override
    public ScenarioRow findScenarioRow(long companyId, long id) {
        Scenario s = requireScenario(companyId, id);
        String createdByName = s.createdBy() != null
                ? userUseCase.findNamesByIds(companyId, List.of(s.createdBy())).get(s.createdBy())
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
    public Map<Long, String> findNamesByIds(long companyId, Collection<Long> ids) {
        return repository.findNamesByIds(companyId, ids);
    }

    @Override
    public CanvasGraphDto getCanvas(long companyId, long id) {
        Scenario scenario = requireScenario(companyId, id);
        return ScenarioCanvasConverter.toCanvasGraph(scenario);
    }

    @Override
    @Transactional
    public ScenarioRow updateCanvas(long companyId, long id, CanvasGraphDto canvas) {
        Scenario current = requireScenario(companyId, id);
        ScenarioDefinition def = ScenarioCanvasConverter.toScenarioDefinition(canvas, current.definition());
        String name = canvas.name() != null && !canvas.name().isBlank() ? canvas.name() : current.name();
        UpdateScenarioRequest request = new UpdateScenarioRequest(name, current.description(), canvas.version(), def);
        return update(companyId, id, request);
    }

    @Override
    public CanvasGraphDto autoLayoutCanvas(CanvasGraphDto canvas) {
        return ScenarioCanvasConverter.autoLayout(canvas);
    }

    @Override
    public CanvasDryRunResponse dryRunStep(long companyId, CanvasDryRunRequest request) {
        ScenarioDefinition def = null;
        if (request.customCanvas() != null) {
            Scenario fallback = request.scenarioId() != null ? findById(companyId, request.scenarioId()) : null;
            def = ScenarioCanvasConverter.toScenarioDefinition(request.customCanvas(), fallback != null ? fallback.definition() : null);
        } else if (request.scenarioId() != null) {
            Scenario s = requireScenario(companyId, request.scenarioId());
            def = s.definition();
        }
        if (def == null) {
            throw new ValidationException(ErrorCode.PARAM_REQUIRED, "scenarioId or customCanvas must be provided");
        }
        return ScenarioCanvasSimulator.step(def, request);
    }

    /** Every problem the validator found goes to the client as its own entry of {@code errors}. */
    private void requireValid(ScenarioDefinition definition) {
        List<String> errors = ScenarioValidator.validate(definition);
        if (!errors.isEmpty()) {
            throw ValidationException.withErrors(ErrorCode.SCENARIO_DEFINITION_INVALID, errors, errors.size());
        }
        requireKnownStageModels(definition);
    }

    /**
     * A stage's model is checked against the catalog here, so a typo is a 400 on save
     * instead of a call that fails on the first turn of that stage. Cascade only: a stage
     * model is read by the cascade dialog, a realtime engine keeps its own for the call.
     */
    private void requireKnownStageModels(ScenarioDefinition definition) {
        for (StageDef stage : definition.stages()) {
            String model = stage.model();
            if (model != null && !model.isBlank()
                    && !aiModelUseCase.isSelectable(AiModelKind.LLM, PipelineMode.CASCADE, model.trim())) {
                throw new ValidationException(ErrorCode.AI_MODEL_UNKNOWN, model.trim(),
                        aiModelUseCase.findSelectableIds(AiModelKind.LLM, PipelineMode.CASCADE));
            }
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
