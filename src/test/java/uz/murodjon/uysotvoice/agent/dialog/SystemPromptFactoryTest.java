package uz.murodjon.uysotvoice.agent.dialog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import uz.murodjon.uysotvoice.shared.dialog.DialogState;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The prompt carries the debtor's facts and the guardrails. If a fact goes missing
 * the model invents one, and §4.4 says the numbers are never the model's to choose.
 * The prefix/annex split additionally has to hold: the prefix is what the provider
 * caches, so it must not move when the FSM does.
 */
class SystemPromptFactoryTest {

    private final SystemPromptFactory factory = new SystemPromptFactory();

    private static DialogSession session(CallContext context, String language) {
        return new DialogSession("chan-1", language, null, context, null, null, null, 1L, null);
    }

    private static CallContext fullContext() {
        return new CallContext("Aziz Karimov", new BigDecimal("1500000"), "so'm",
                LocalDate.of(2026, 7, 1), "UY-2026-00123", "To'lov sanasini kelishish.");
    }

    @Test
    void carriesEveryFactVerbatim() {
        String prompt = factory.stablePrefix(session(fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Aziz Karimov")
                .contains("1500000")
                .contains("so'm")
                .contains("2026-07-01")
                .contains("UY-2026-00123")
                .contains("To'lov sanasini kelishish.");
    }

    @Test
    void namesTodaysDate() {
        // Without it the model invents a year for "kelasi oyning 5-sanasi" and the
        // recordPaymentPromise guardrail then rejects its own answer as a past date.
        String prompt = factory.stablePrefix(session(fullContext(), "uz-UZ"));

        assertThat(prompt).contains(LocalDate.now().toString());
    }

    @Test
    void missingFactsBecomeDashesRatherThanNull() {
        CallContext empty = new CallContext(null, null, null, null, null, null);

        String prompt = factory.stablePrefix(session(empty, "uz-UZ"));

        assertThat(prompt).doesNotContain("null");
        assertThat(prompt).contains("—");
    }

    @ParameterizedTest
    @EnumSource(DialogState.class)
    void everyStateHasAnObjectiveAndAllowedTransitions(DialogState state) {
        DialogSession s = session(fullContext(), "uz-UZ");
        s.setState(state);

        String annex = factory.turnAnnex(s);

        assertThat(annex).contains("JORIY BOSQICH: " + state.name());
        assertThat(annex).contains("Ruxsat etilgan keyingi bosqichlar:");
    }

    @Test
    void theCachedPrefixDoesNotMoveWithTheState() {
        // The whole point of the split: the provider's context cache keys on this
        // prefix, so a transition must not change a single byte of it.
        DialogSession s = session(fullContext(), "uz-UZ");
        String atGreeting = factory.stablePrefix(s);

        s.setState(DialogState.PAYMENT_DATE);

        assertThat(factory.stablePrefix(s)).isEqualTo(atGreeting);
        assertThat(atGreeting).doesNotContain("JORIY BOSQICH");
    }

    @Test
    void aBargeInIsReportedInTheAnnexOnly() {
        DialogSession s = session(fullContext(), "uz-UZ");
        s.setLastAgentText("Qarzingiz bo'yicha");
        s.setInterrupted(true);

        assertThat(factory.turnAnnex(s)).contains("bo'ldi").contains("Qarzingiz bo'yicha");
        assertThat(factory.stablePrefix(s)).doesNotContain("Qarzingiz bo'yicha");
    }

    @Test
    void statesTheConversationLanguage() {
        assertThat(factory.stablePrefix(session(fullContext(), "ru-RU"))).contains("rus tilida");
        assertThat(factory.stablePrefix(session(fullContext(), "uz-UZ"))).contains("o'zbek tilida");
    }

    @Test
    void alwaysCarriesTheGuardrails() {
        String prompt = factory.stablePrefix(session(fullContext(), "uz-UZ"));

        assertThat(prompt)
                .contains("Chegirma")            // never offer a discount (§4.4)
                .contains("recordDoNotCall")     // opt-out route (§11.4)
                .contains("requestHumanTransfer"); // human escalation must always exist (§11.6)
    }

    @Test
    void tellsTheModelNotToRepeatASpokenDisclosure() {
        // §11.1: the disclosure is spoken from code; repeating it sounds broken.
        DialogSession s = session(fullContext(), "uz-UZ");
        assertThat(factory.stablePrefix(s)).doesNotContain("TAKRORLAMA");

        s.setDisclosureSpoken(true);
        assertThat(factory.stablePrefix(s)).contains("TAKRORLAMA");
    }
}
