package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.application.port.output.ScenarioRepository;
import uz.murodjon.robotcallv2.scenario.domain.entity.Scenario;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MultiAgentRouterTest {

    private ScenarioRepository scenarioRepo;
    private DialogEngine dialogEngine;
    private MultiAgentRouter router;

    @BeforeEach
    void setUp() {
        scenarioRepo = mock(ScenarioRepository.class);
        dialogEngine = mock(DialogEngine.class);
        router = new MultiAgentRouter(scenarioRepo, dialogEngine);
    }

    @Test
    void throwsNotFoundWhenSessionDoesNotExistOrEnded() {
        when(dialogEngine.findSession("chan-1")).thenReturn(null);

        assertThatThrownBy(() -> router.routeToScenario("chan-1", "order-confirm"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsNotFoundWhenTargetScenarioNotFound() {
        DialogSession session = mock(DialogSession.class);
        when(session.isEnded()).thenReturn(false);
        when(dialogEngine.findSession("chan-1")).thenReturn(session);
        when(scenarioRepo.findActiveByKey("unknown-scenario")).thenReturn(null);

        assertThatThrownBy(() -> router.routeToScenario("chan-1", "unknown-scenario"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void routesToTargetScenarioSuccessfully() {
        DialogSession session = mock(DialogSession.class);
        when(session.isEnded()).thenReturn(false);
        when(dialogEngine.findSession("chan-1")).thenReturn(session);

        ScenarioDefinition currentDef = ScenarioFixtures.debtCollection();
        when(session.scenario()).thenReturn(currentDef);

        StageDef newStage = new StageDef("INTRO", "Stage 1", List.of(), List.of());
        ScenarioDefinition targetDef = new ScenarioDefinition(
                List.of(newStage), List.of(), List.of(), List.of(), "Sales Bot", List.of(), null);
        Scenario targetScenario = new Scenario(
                2L, "sales", 1, "Sales Agent", null, false, true, targetDef, Instant.now(), null);

        when(scenarioRepo.findActiveByKey("sales")).thenReturn(targetScenario);

        boolean success = router.routeToScenario("chan-1", "sales");

        assertThat(success).isTrue();
        verify(session).setState("INTRO");
    }
}
