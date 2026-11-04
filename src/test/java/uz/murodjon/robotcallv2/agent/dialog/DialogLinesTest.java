package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgentPhrases;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.scenario.domain.entity.StageDef;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogLinesTest {

    private DialogSession session;
    private ScenarioDefinition scenario;

    @BeforeEach
    void setUp() {
        session = mock(DialogSession.class);
        scenario = mock(ScenarioDefinition.class);
        when(session.scenario()).thenReturn(scenario);
        when(session.language()).thenReturn("uz-UZ");
        when(session.isDisclosureSpoken()).thenReturn(true);
        when(scenario.findStartStage()).thenReturn(new StageDef("GREETING", "salom", List.of(), List.of()));
    }

    @Test
    void fallbackSaysTheStagesOwnLineWithFactsFilledIn() {
        StageDef stage = StageDef.builder().id("PAYMENT_DATE").purpose("sana")
                .fallbackLine(" {{clientName}} aka, qaysi sanada to'lov qila olasiz? ").build();
        when(session.state()).thenReturn("PAYMENT_DATE");
        when(scenario.findStage("PAYMENT_DATE")).thenReturn(stage);
        when(session.outcome()).thenReturn(Map.of("clientName", "Murod"));

        assertThat(DialogLines.fallback(session)).isEqualTo("Murod aka, qaysi sanada to'lov qila olasiz?");
    }

    @Test
    void fallbackAsksToRepeatInAStageWithoutALine() {
        when(session.state()).thenReturn("REASON_INQUIRY");
        when(scenario.findStage("REASON_INQUIRY"))
                .thenReturn(new StageDef("REASON_INQUIRY", "sabab", List.of(), List.of()));

        assertThat(DialogLines.fallback(session)).isEqualTo(DialogPhrases.didNotCatch("uz-UZ"));
    }

    @Test
    void agentsOwnLinesAreSpokenInItsLanguage() {
        when(session.agent()).thenReturn(agentWithPhrases("uz-UZ"));

        assertThat(DialogLines.stillThere(session)).isEqualTo("Murod aka, aloqadamisiz?");
        assertThat(DialogLines.hasOwnStillThere(session)).isTrue();
        assertThat(DialogLines.farewell(session)).isEqualTo("Yaxshi qoling!");
        assertThat(DialogLines.thinking(session)).containsExactly("Bir zum.");
        // Not set by the agent: the standard line.
        assertThat(DialogLines.gratitudeReply(session)).isEqualTo(DialogPhrases.gratitudeReply("uz-UZ"));
        assertThat(DialogLines.backchannels(session)).isEqualTo(DialogPhrases.backchannels("uz-UZ"));
    }

    @Test
    void agentsOwnLinesAreNotSpokenInAnotherLanguage() {
        when(session.agent()).thenReturn(agentWithPhrases("uz-UZ"));
        when(session.language()).thenReturn("ru-RU");

        assertThat(DialogLines.stillThere(session)).isEqualTo(DialogPhrases.stillThere("ru-RU"));
        assertThat(DialogLines.hasOwnStillThere(session)).isFalse();
        assertThat(DialogLines.thinking(session)).isEqualTo(DialogPhrases.thinking("ru-RU"));
    }

    @Test
    void agentsDidNotCatchWhenTheStageHasNoLine() {
        when(session.agent()).thenReturn(agentWithPhrases("uz-UZ"));
        when(session.state()).thenReturn("PAYMENT_DATE");
        when(scenario.findStage("PAYMENT_DATE")).thenReturn(new StageDef("PAYMENT_DATE", "sana", List.of(), List.of()));

        assertThat(DialogLines.fallback(session)).isEqualTo("Kechirasiz, yana bir bor ayting.");
    }

    private static AiAgent agentWithPhrases(String language) {
        AiAgent agent = mock(AiAgent.class);
        when(agent.language()).thenReturn(language);
        when(agent.phrases()).thenReturn(new AiAgentPhrases(" Murod aka, aloqadamisiz? ", null,
                "Kechirasiz, yana bir bor ayting.", "Yaxshi qoling!", "", null, List.of("Bir zum."), null));
        return agent;
    }

    @Test
    void transferringFallsBackToTheStandardLineWithoutAnAgent() {
        assertThat(DialogLines.transferring(session)).isEqualTo(DialogPhrases.transferring("uz-UZ"));
    }
}
