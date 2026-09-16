package uz.murodjon.robotcallv2.secret.application.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRowTest {

    @Test
    void showsAtMostTheLastTwoCharacters() {
        assertThat(SecretRow.mask("sk-live-abcdef123456")).isEqualTo("******56");
    }

    /** The old form showed six characters of an eight-character secret. */
    @Test
    void neverShowsMoreThanAQuarterOfTheSecret() {
        assertThat(SecretRow.mask("abcdefgh")).isEqualTo("******gh");
        assertThat(SecretRow.mask("abcd")).isEqualTo("******d");
        assertThat(SecretRow.mask("abc")).isEqualTo("******");
    }

    @Test
    void aBlankSecretIsFullyMasked() {
        assertThat(SecretRow.mask(null)).isEqualTo("******");
        assertThat(SecretRow.mask("   ")).isEqualTo("******");
    }
}
