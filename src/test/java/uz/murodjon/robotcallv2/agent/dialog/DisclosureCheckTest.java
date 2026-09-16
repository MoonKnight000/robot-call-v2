package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DisclosureCheckTest {

    private static final String DISCLOSURE =
            "Assalomu alaykum! Men Uysot kompaniyasining robotiman. Suhbat yozib olinmoqda.";

    @Test
    void coveredWhenSaidInOtherWords() {
        assertThat(DisclosureCheck.covered(DISCLOSURE,
                "Assalomu alaykum, men Uysot kompaniyasining robotiman, suhbatimiz yozib olinmoqda. "
                        + "Murodjon aka bilan gaplashyapmanmi?")).isTrue();
    }

    @Test
    void notCoveredWhenTheCallerCutInAfterTheGreeting() {
        assertThat(DisclosureCheck.covered(DISCLOSURE, "Assalomu alaykum")).isFalse();
    }

    @Test
    void apostropheVariantsCompareEqual() {
        assertThat(DisclosureCheck.covered("Qo‘ng‘iroq yozib olinmoqda", "qo'ng'iroq yozib olinmoqda")).isTrue();
    }

    @Test
    void nothingToSayIsTriviallyCovered() {
        assertThat(DisclosureCheck.covered(null, "")).isTrue();
        assertThat(DisclosureCheck.covered("  ", null)).isTrue();
    }

    @Test
    void silenceDoesNotCoverAnything() {
        assertThat(DisclosureCheck.covered(DISCLOSURE, " ")).isFalse();
    }
}
