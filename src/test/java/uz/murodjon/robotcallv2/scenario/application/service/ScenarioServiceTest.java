package uz.murodjon.robotcallv2.scenario.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aimodel.application.port.input.AiModelUseCase;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.scenario.application.dto.*;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.security.CurrentUser;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.user.application.port.input.UserUseCase;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScenarioServiceTest {

    private ScenarioRepository repo;
    private CurrentUser currentUser;
    private UserUseCase users;
    private AuditService audit;
    private AiModelUseCase aiModels;
    private ScenarioService scenarioService;

    @BeforeEach
    void setUp() {
        repo = mock(ScenarioRepository.class);
        currentUser = mock(CurrentUser.class);
        users = mock(UserUseCase.class);
        audit = mock(AuditService.class);
        aiModels = mock(AiModelUseCase.class);

        when(currentUser.id()).thenReturn(Optional.of(1L));

        scenarioService = new ScenarioService(repo, currentUser, users, audit, aiModels);
    }

    private ScenarioDefinition validDefinition() {
        return new ScenarioDefinition(
                List.of(
                        new StageDef("START", "Salomlash", List.of("END"), List.of()),
                        new StageDef("END", "Yakunla", List.of(), List.of())
                ),
                List.of(new FactField("name", "string", true)),
                List.of(),
                List.of(new OutcomeField("success", "boolean", "Natija")),
                "System prompt",
                List.of("Rule 1"),
                null
        );
    }

    private Scenario sampleScenario(long id, String key, boolean builtin) {
        return new Scenario(
                id, key, 1, "Test Scenario", "Description",
                builtin, true, validDefinition(), Instant.now(), 1L
        );
    }

    @Test
    void createSuccess() {
        CreateScenarioRequest req = new CreateScenarioRequest(
                "custom-key", "My Scenario", "Desc", validDefinition());

        when(repo.existsByKey("custom-key")).thenReturn(false);
        when(repo.create(eq(1L), eq("custom-key"), eq("My Scenario"), eq("Desc"), eq(false), any(), eq(1L)))
                .thenReturn(10L);
        when(repo.find(1L, 10L)).thenReturn(sampleScenario(10L, "custom-key", false));
        when(users.findNamesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin User"));

        ScenarioRow created = scenarioService.create(1L, req);

        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.scenarioKey()).isEqualTo("custom-key");
        verify(audit).record(eq(1L), eq("SCENARIO_CREATE"), eq("scenario"), eq("10"), eq("custom-key"));
    }

    @Test
    void createRejectsAStageModelOutsideTheCatalog() {
        ScenarioDefinition definition = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("GREETING")
                                .purpose("Say hello")
                                .model("made-up-model")
                                .build(),
                        new StageDef("CLOSE", "Bye", List.of(), List.of())
                ),
                List.of(),
                List.of(),
                List.of(new OutcomeField("res", "string", "Outcome")),
                "role",
                List.of(),
                null
        );

        when(aiModels.isSelectable(AiModelKind.LLM, PipelineMode.CASCADE, "made-up-model")).thenReturn(false);
        when(aiModels.findSelectableIds(AiModelKind.LLM, PipelineMode.CASCADE)).thenReturn(List.of("gpt-4o", "gemini-2.0-flash"));

        assertThatThrownBy(() -> scenarioService.create(1L,
                new CreateScenarioRequest("test", "Test", "Desc", definition)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createAcceptsAStageModelFromCascadeCatalog() {
        ScenarioDefinition definition = new ScenarioDefinition(
                List.of(
                        StageDef.builder()
                                .id("GREETING")
                                .purpose("Say hello")
                                .model("gemini-2.0-flash")
                                .build(),
                        new StageDef("CLOSE", "Bye", List.of(), List.of())
                ),
                List.of(),
                List.of(),
                List.of(new OutcomeField("res", "string", "Outcome")),
                "role",
                List.of(),
                null
        );

        when(aiModels.isSelectable(AiModelKind.LLM, PipelineMode.CASCADE, "gemini-2.0-flash")).thenReturn(true);
        when(repo.existsByKey("test")).thenReturn(false);
        when(repo.create(eq(1L), eq("test"), any(), any(), eq(false), any(), eq(1L))).thenReturn(11L);
        when(repo.find(1L, 11L)).thenReturn(new Scenario(11L, "test", 1, "Test", "D", false, true, definition, Instant.now(), 1L));
        when(users.findNamesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin"));

        ScenarioRow created = scenarioService.create(1L, new CreateScenarioRequest("test", "Test", "D", definition));
        assertThat(created).isNotNull();
    }

    @Test
    void updateBuiltinThrowsForbidden() {
        when(repo.find(1L, 5L)).thenReturn(sampleScenario(5L, "builtin-key", true));

        UpdateScenarioRequest req = new UpdateScenarioRequest(
                "Updated", "Desc", validDefinition(), null);

        assertThatThrownBy(() -> scenarioService.update(1L, 5L, req))
                .isInstanceOf(ForbiddenException.class);
    }

    /** The editor read version 1, but version 2 has been saved since: nothing is retired or inserted. */
    @Test
    void updateAnswers409WhenTheScenarioWasSavedAfterItWasRead() {
        when(repo.find(1L, 5L)).thenReturn(sampleScenario(5L, "custom-key", false));
        when(repo.findMaxVersion("custom-key")).thenReturn(2);
        when(repo.deactivate("custom-key", 1)).thenReturn(false);

        assertThatThrownBy(() -> scenarioService.update(1L, 5L,
                new UpdateScenarioRequest("New", "D", 1, validDefinition())))
                .isInstanceOf(ConflictException.class);
        verify(repo, never()).insertVersion(anyLong(), anyString(), anyInt(), any(), any(), anyBoolean(), any(), any());
    }

    /** A client that predates the version field replaces whatever version is the latest, as before. */
    @Test
    void updateWithoutVersionReplacesTheLatestVersion() {
        when(repo.find(1L, 5L)).thenReturn(sampleScenario(5L, "custom-key", false));
        when(repo.findMaxVersion("custom-key")).thenReturn(2);
        when(repo.deactivate("custom-key", 2)).thenReturn(true);
        when(repo.insertVersion(eq(1L), eq("custom-key"), eq(3), eq("New"), eq("D"), eq(false), any(), eq(1L)))
                .thenReturn(9L);
        when(repo.find(1L, 9L)).thenReturn(sampleScenario(9L, "custom-key", false));
        when(users.findNamesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin"));

        ScenarioRow updated = scenarioService.update(1L, 5L, new UpdateScenarioRequest("New", "D", null, validDefinition()));

        assertThat(updated.id()).isEqualTo(9L);
        verify(repo).deactivate("custom-key", 2);
    }

    @Test
    void cloneSuccess() {
        Scenario source = sampleScenario(5L, "source-scenario", false);
        when(repo.find(1L, 5L)).thenReturn(source);
        when(repo.existsByKey("cloned-scenario")).thenReturn(false);
        when(repo.create(eq(1L), eq("cloned-scenario"), eq("Cloned"), eq("Description"), eq(false), any(), eq(1L)))
                .thenReturn(6L);
        when(repo.find(1L, 6L)).thenReturn(sampleScenario(6L, "cloned-scenario", false));
        when(users.findNamesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin"));

        ScenarioRow cloned = scenarioService.clone(1L, 5L, new CloneScenarioRequest("cloned-scenario", "Cloned"));

        assertThat(cloned).isNotNull();
        assertThat(cloned.id()).isEqualTo(6L);
        verify(audit).record(eq(1L), eq("SCENARIO_CLONE"), eq("scenario"), eq("6"), anyString());
    }

    @Test
    void requireScenarioThrowsNotFoundWhenMissing() {
        when(repo.find(1L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> scenarioService.requireScenario(1L, 999L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCanvasReturnsGraphRepresentation() {
        when(repo.find(1L, 5L)).thenReturn(sampleScenario(5L, "custom-key", false));

        CanvasGraphDto canvas = scenarioService.getCanvas(1L, 5L);

        assertThat(canvas).isNotNull();
        assertThat(canvas.scenarioId()).isEqualTo(5L);
        assertThat(canvas.nodes()).isNotEmpty();
        assertThat(canvas.edges()).isNotEmpty();
    }

    @Test
    void updateCanvasSavesUpdatedDefinition() {
        Scenario current = sampleScenario(5L, "custom-key", false);
        when(repo.find(1L, 5L)).thenReturn(current);
        when(repo.findMaxVersion("custom-key")).thenReturn(1);
        when(repo.deactivate("custom-key", 1)).thenReturn(true);
        when(repo.insertVersion(eq(1L), eq("custom-key"), eq(2), any(), any(), eq(false), any(), eq(1L)))
                .thenReturn(12L);
        when(repo.find(1L, 12L)).thenReturn(sampleScenario(12L, "custom-key", false));
        when(users.findNamesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin"));

        CanvasGraphDto canvas = scenarioService.getCanvas(1L, 5L);
        ScenarioRow updated = scenarioService.updateCanvas(1L, 5L, canvas);

        assertThat(updated).isNotNull();
        assertThat(updated.id()).isEqualTo(12L);
        verify(repo).insertVersion(eq(1L), eq("custom-key"), eq(2), any(), any(), eq(false), any(), eq(1L));
    }

    @Test
    void dryRunStepExecutesSimulationTurn() {
        when(repo.find(1L, 5L)).thenReturn(sampleScenario(5L, "custom-key", false));

        CanvasDryRunRequest req = new CanvasDryRunRequest(
                5L, "START", "Salom", Map.of("name", "Ali"), Map.of(), null);

        CanvasDryRunResponse response = scenarioService.dryRunStep(1L, req);

        assertThat(response).isNotNull();
        assertThat(response.previousNodeId()).isEqualTo("START");
        assertThat(response.nextNodeId()).isEqualTo("END");
    }
}
