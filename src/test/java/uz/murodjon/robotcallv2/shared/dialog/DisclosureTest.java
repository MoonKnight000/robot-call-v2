package uz.murodjon.robotcallv2.shared.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The §11.1 disclosure is a legal obligation the platform owes the caller, and this is
 * the one place configuration gets to touch it. So the properties worth pinning down are
 * the refusals: a company (or a scenario) may reword the notice, never drop it, and
 * never leave a caller hearing it in a language they were not called in.
 */
class DisclosureTest {

    private static final String UZ = "Assalomu alaykum! Bu {company} kompaniyasining avtomatik ovozli xizmati. "
            + "Suhbat yozib olinmoqda.";
    private static final String RU = "Это автоматический звонок, разговор записывается.";

    @Test
    void usesTheConfiguredWording() {
        String line = Disclosure.resolve("Bu avtomatik xizmat, suhbat yozib olinmoqda.", "uz-UZ", "Uysot");

        assertThat(line).isEqualTo("Bu avtomatik xizmat, suhbat yozib olinmoqda.");
    }

    @Test
    void fallsBackWhenNothingIsConfigured() {
        assertThat(Disclosure.resolve(null, "uz-UZ", "Uysot")).isNull();
        assertThat(Disclosure.resolve("   ", "uz-UZ", "Uysot")).isNull();
    }

    @Test
    void refusesAGreetingDressedUpAsADisclosure() {
        // The whole point of checking at call time: a configured text cannot replace the
        // notice with something friendlier and have the bot speak it.
        assertThat(Disclosure.resolve("Assalomu alaykum, qanday yordam bera olaman?", "uz-UZ", "Uysot")).isNull();
    }

    @Test
    void refusesADisclosureThatOmitsTheRecording() {
        assertThat(Disclosure.resolve("Bu avtomatik ovozli xizmat.", "uz-UZ", "Uysot")).isNull();
    }

    @Test
    void refusesADisclosureThatOmitsBeingAutomated() {
        assertThat(Disclosure.resolve("Suhbat yozib olinmoqda.", "uz-UZ", "Uysot")).isNull();
    }

    @Test
    void refusesAWordingWrittenInAnotherLanguage() {
        // A ru-RU target under an Uzbek wording must not be read an Uzbek notice.
        assertThat(Disclosure.resolve(UZ, "ru-RU", "Uysot")).isNull();
        assertThat(Disclosure.resolve(RU, "uz-UZ", "Uysot")).isNull();
    }

    @Test
    void speaksARussianWordingOnARussianCall() {
        assertThat(Disclosure.resolve(RU, "ru-RU", "Uysot")).isEqualTo(RU);
    }

    @Test
    void namesTheCallingCompany() {
        assertThat(Disclosure.resolve(UZ, "uz-UZ", "Uysot"))
                .isEqualTo("Assalomu alaykum! Bu Uysot kompaniyasining avtomatik ovozli xizmati. "
                        + "Suhbat yozib olinmoqda.");
    }

    @Test
    void leavesNoGapWhenThereIsNoCompanyToName() {
        String line = Disclosure.resolve("Bu {company} avtomatik xizmati, suhbat yozib olinmoqda.", "uz-UZ", null);

        assertThat(line).isEqualTo("Bu avtomatik xizmati, suhbat yozib olinmoqda.");
    }

    @Test
    void disclosesAcceptsBothLanguages() {
        assertThat(Disclosure.discloses(UZ)).isTrue();
        assertThat(Disclosure.discloses(RU)).isTrue();
        assertThat(Disclosure.discloses("Salom!")).isFalse();
        assertThat(Disclosure.discloses(null)).isFalse();
    }
}
