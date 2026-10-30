package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.aiagent.domain.entity.AiAgent;
import uz.murodjon.robotcallv2.company.domain.entity.CompanyConfig;
import uz.murodjon.robotcallv2.scenario.domain.entity.ScenarioDefinition;
import uz.murodjon.robotcallv2.shared.dialog.AgentPersona;
import uz.murodjon.robotcallv2.shared.dialog.DialogPhrases;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The rules both pipelines share — a persona the company was not allowed, a disclosure
 * worded away, or a transferred call that gets hung up instead would each put a call on
 * the wrong side of §11.
 */
class DialogPolicyTest {

    // ------------------------------------------------------------------
    // Persona gating
    // ------------------------------------------------------------------

    @Test
    void callWithoutAnAgentSpeaksAsAnAssistant() {
        assertThat(DialogPolicy.resolvePersona(null, null)).isEqualTo(AgentPersona.AI_ASSISTANT);
    }

    @Test
    void humanLikeAgentKeepsItsPersonaWhereTheCompanyAllowsIt() {
        AiAgent agent = agentSpeakingAs(AgentPersona.HUMAN_LIKE);

        assertThat(DialogPolicy.resolvePersona(agent, companyAllowingHumanLike(true)))
                .isEqualTo(AgentPersona.HUMAN_LIKE);
        // A company with no config row has not been refused anything.
        assertThat(DialogPolicy.resolvePersona(agent, null)).isEqualTo(AgentPersona.HUMAN_LIKE);
    }

    @Test
    void humanLikeAgentDisclosesWhereTheCompanyIsNotAllowedIt() {
        assertThat(DialogPolicy.resolvePersona(agentSpeakingAs(AgentPersona.HUMAN_LIKE),
                companyAllowingHumanLike(false))).isEqualTo(AgentPersona.AI_ASSISTANT);
    }

    @Test
    void assistantAgentStaysAnAssistantWhateverTheCompanyAllows() {
        assertThat(DialogPolicy.resolvePersona(agentSpeakingAs(AgentPersona.AI_ASSISTANT),
                companyAllowingHumanLike(true))).isEqualTo(AgentPersona.AI_ASSISTANT);
    }

    // ------------------------------------------------------------------
    // Disclosure wording
    // ------------------------------------------------------------------

    @Test
    void disclosureFallsBackToThePlatformWordingWhenTheScenarioHasNone() {
        assertThat(DialogPolicy.resolveDisclosureLine(null, "uz-UZ", "Uysot"))
                .isEqualTo(DialogPhrases.disclosure("uz-UZ", "Uysot"));
    }

    @Test
    void scenarioWordingIsUsedWhenItDisclosesBothFacts() {
        ScenarioDefinition scenario = mock(ScenarioDefinition.class);
        when(scenario.disclosureText()).thenReturn("Robot qo'ng'iroq qilmoqda, suhbat yozuvga olinadi.");

        assertThat(DialogPolicy.resolveDisclosureLine(scenario, "uz-UZ", "Uysot"))
                .isEqualTo("Robot qo'ng'iroq qilmoqda, suhbat yozuvga olinadi.");
    }

    @Test
    void scenarioWordingThatLeavesOutTheRecordingFallsBackToThePlatform() {
        ScenarioDefinition scenario = mock(ScenarioDefinition.class);
        when(scenario.disclosureText()).thenReturn("Bu avtomatik tizim.");

        assertThat(DialogPolicy.resolveDisclosureLine(scenario, "uz-UZ", "Uysot"))
                .isEqualTo(DialogPhrases.disclosure("uz-UZ", "Uysot"));
    }

    // ------------------------------------------------------------------
    // Closing action
    // ------------------------------------------------------------------

    @Test
    void transferredCallGoesToTheOperator() {
        Runnable transfer = () -> { };
        Runnable hangup = () -> { };

        assertThat(DialogPolicy.selectClosingAction(Disposition.TRANSFERRED, transfer, hangup)).isSameAs(transfer);
    }

    @Test
    void everyOtherEndingHangsUp() {
        Runnable transfer = () -> { };
        Runnable hangup = () -> { };

        assertThat(DialogPolicy.selectClosingAction(Disposition.PROMISE_TO_PAY, transfer, hangup)).isSameAs(hangup);
        assertThat(DialogPolicy.selectClosingAction(null, transfer, hangup)).isSameAs(hangup);
        // Transferred, but the call has nowhere to be transferred to.
        assertThat(DialogPolicy.selectClosingAction(Disposition.TRANSFERRED, null, hangup)).isSameAs(hangup);
    }

    // ------------------------------------------------------------------
    // Limits
    // ------------------------------------------------------------------

    @Test
    void zeroMaxTurnsMeansNoTurnCap() {
        assertThat(DialogPolicy.isTurnCapExceeded(1, 0)).isFalse();
        assertThat(DialogPolicy.isTurnCapExceeded(500, 0)).isFalse();
        assertThat(DialogPolicy.isTurnCapExceeded(1, -1)).isFalse();
    }

    @Test
    void turnCapClosesOnlyTheTurnAfterTheLastAllowedOne() {
        assertThat(DialogPolicy.isTurnCapExceeded(25, 25)).isFalse();
        assertThat(DialogPolicy.isTurnCapExceeded(26, 25)).isTrue();
    }

    @Test
    void tokenBudgetIsSpentOnceTheCapIsReached() {
        assertThat(DialogPolicy.isTokenBudgetSpent(0, 10_000)).isFalse();
        assertThat(DialogPolicy.isTokenBudgetSpent(1000, 999)).isFalse();
        assertThat(DialogPolicy.isTokenBudgetSpent(1000, 1000)).isTrue();
    }

    // ------------------------------------------------------------------
    // Fact guard switch
    // ------------------------------------------------------------------

    @Test
    void factGuardFindsAFigureThatIsNotInTheFacts() {
        assertThat(policyWithFactGuard(true).findFactViolations("Qarzingiz 9 999 999 so'm.",
                ScenarioFixtures.debtCollection(), ScenarioFixtures.fullContext(), Set.of())).isNotEmpty();
    }

    @Test
    void factGuardSwitchedOffFindsNothing() {
        assertThat(policyWithFactGuard(false).findFactViolations("Qarzingiz 9 999 999 so'm.",
                ScenarioFixtures.debtCollection(), ScenarioFixtures.fullContext(), Set.of())).isEmpty();
    }

    private static DialogPolicy policyWithFactGuard(boolean on) {
        DialogProperties dialogProperties = mock(DialogProperties.class);
        when(dialogProperties.factGuard()).thenReturn(on);
        return new DialogPolicy(dialogProperties, null);
    }

    private static AiAgent agentSpeakingAs(AgentPersona persona) {
        AiAgent agent = mock(AiAgent.class);
        when(agent.persona()).thenReturn(persona);
        return agent;
    }

    private static CompanyConfig companyAllowingHumanLike(boolean allowed) {
        CompanyConfig config = mock(CompanyConfig.class);
        when(config.humanLikePersonaAllowed()).thenReturn(allowed);
        return config;
    }
}
