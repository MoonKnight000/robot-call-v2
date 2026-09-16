package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeHeardPartTest {

    @Test
    void keepsTheShareOfWordsThatWasPlayed() {
        assertThat(RealtimeDialogEngine.heardPart("bir ikki uch to'rt", 0.5)).isEqualTo("bir ikki");
        assertThat(RealtimeDialogEngine.heardPart("bir ikki uch to'rt", 0.13)).isEqualTo("bir");
    }

    @Test
    void aFullyPlayedReplyIsKeptWhole() {
        assertThat(RealtimeDialogEngine.heardPart(" bir ikki uch ", 1.0)).isEqualTo("bir ikki uch");
    }

    @Test
    void nothingHeardIsNull() {
        assertThat(RealtimeDialogEngine.heardPart("bir ikki uch to'rt", 0.1)).isNull();
        assertThat(RealtimeDialogEngine.heardPart("  ", 0.9)).isNull();
    }
}
