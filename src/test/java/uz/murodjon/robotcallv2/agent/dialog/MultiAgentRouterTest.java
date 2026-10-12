package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MultiAgentRouterTest {

    private static final ScenarioDefinition SALES = new ScenarioDefinition(
            List.of(new StageDef("INTRO", "Stage 1", List.of(), List.of())),
            List.of(), List.of(), List.of(), "Sales Bot", List.of(), null);

    private DialogEngine dialogEngine;
    private MultiAgentRouter router;

    @BeforeEach
    void setUp() {
        dialogEngine = mock(DialogEngine.class);
        router = new MultiAgentRouter(dialogEngine);
    }

    @Test
    void refusesACallWithoutACascadeDialog() {
        when(dialogEngine.findSession("chan-1")).thenReturn(null);

        assertThatThrownBy(() -> router.routeToScenario("chan-1", SALES))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesADialogThatHasEnded() {
        DialogSession session = mock(DialogSession.class);
        when(session.isEnded()).thenReturn(true);
        when(dialogEngine.findSession("chan-1")).thenReturn(session);

        assertThatThrownBy(() -> router.routeToScenario("chan-1", SALES))
                .isInstanceOf(ConflictException.class);
        verify(session, never()).switchScenario(any());
    }

    @Test
    void switchesTheSessionToTheTarget() {
        DialogSession session = mock(DialogSession.class);
        when(dialogEngine.findSession("chan-1")).thenReturn(session);
        when(session.state()).thenReturn("GREETING", "INTRO");

        String stage = router.routeToScenario("chan-1", SALES);

        verify(session).switchScenario(SALES);
        assertThat(stage).isEqualTo("INTRO");
    }
}
