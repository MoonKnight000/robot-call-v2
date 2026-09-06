package uz.murodjon.robotcallv2.scenario.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.scenario.application.dto.CloneScenarioRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.CreateScenarioRequest;
import uz.murodjon.robotcallv2.scenario.application.dto.ScenarioRow;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.*;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ForbiddenException;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.user.application.service.CurrentUser;
import uz.murodjon.robotcallv2.user.application.service.UserService;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ScenarioServiceTest {

    private ScenarioRepository repo;
    private CurrentUser currentUser;
    private UserService users;
    private AuditService audit;
    private ScenarioService scenarioService;

    @BeforeEach
    void setUp() {
        repo = mock(ScenarioRepository.class);
        currentUser = mock(CurrentUser.class);
        users = mock(UserService.class);
        audit = mock(AuditService.class);

        when(currentUser.id()).thenReturn(Optional.of(1L));

        scenarioService = new ScenarioService(repo, currentUser, users, audit);
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
        when(users.namesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin User"));

        ScenarioRow created = scenarioService.create(1L, req);

        assertThat(created).isNotNull();
        assertThat(created.id()).isEqualTo(10L);
        assertThat(created.scenarioKey()).isEqualTo("custom-key");
        verify(audit).record(eq("SCENARIO_CREATE"), eq("scenario"), eq("10"), eq("custom-key"));
    }

    @Test
    void createThrowsConflictWhenKeyExists() {
        CreateScenarioRequest req = new CreateScenarioRequest(
                "existing-key", "My Scenario", "Desc", validDefinition());
        when(repo.existsByKey("existing-key")).thenReturn(true);

        assertThatThrownBy(() -> scenarioService.create(1L, req))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateThrowsForbiddenWhenBuiltin() {
        Scenario builtinScenario = sampleScenario(1L, "debt-collection", true);
        when(repo.find(1L, 1L)).thenReturn(builtinScenario);

        assertThatThrownBy(() -> scenarioService.update(1L, 1L, null))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void cloneSuccess() {
        Scenario source = sampleScenario(5L, "source-scenario", false);
        when(repo.find(1L, 5L)).thenReturn(source);
        when(repo.existsByKey("cloned-scenario")).thenReturn(false);
        when(repo.create(eq(1L), eq("cloned-scenario"), eq("Cloned"), eq("Description"), eq(false), any(), eq(1L)))
                .thenReturn(6L);
        when(repo.find(1L, 6L)).thenReturn(sampleScenario(6L, "cloned-scenario", false));
        when(users.namesByIds(anyLong(), any())).thenReturn(Map.of(1L, "Admin"));

        ScenarioRow cloned = scenarioService.clone(1L, 5L, new CloneScenarioRequest("cloned-scenario", "Cloned"));

        assertThat(cloned).isNotNull();
        assertThat(cloned.id()).isEqualTo(6L);
        verify(audit).record(eq("SCENARIO_CLONE"), eq("scenario"), eq("6"), anyString());
    }

    @Test
    void requireScenarioThrowsNotFoundWhenMissing() {
        when(repo.find(1L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> scenarioService.requireScenario(1L, 999L))
                .isInstanceOf(NotFoundException.class);
    }
}
