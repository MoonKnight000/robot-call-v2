package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmCallersTurnTest {

    @Test
    void aStageJobAfterAQuestionLeavesTheFloorToTheCaller() {
        DialogSession s = mock(DialogSession.class);
        when(s.lastAgentText()).thenReturn("Qaysi turar joy majmuamiz yoki qaysi hudud sizga ko'proq ma'qul? ");

        assertThat(LlmTurnClient.isCallersTurnToAnswer(s, true)).isTrue();
    }

    @Test
    void aTurnTheCallerAskedForIsStillAnswered() {
        DialogSession s = mock(DialogSession.class);
        when(s.lastAgentText()).thenReturn("Qaysi hudud sizga ma'qul?");

        assertThat(LlmTurnClient.isCallersTurnToAnswer(s, false)).isFalse();
    }

    @Test
    void aStageJobAfterAStatementMaySpeak() {
        DialogSession s = mock(DialogSession.class);
        when(s.lastAgentText()).thenReturn("Tushunarli, uch xonali.");

        assertThat(LlmTurnClient.isCallersTurnToAnswer(s, true)).isFalse();
    }
}
