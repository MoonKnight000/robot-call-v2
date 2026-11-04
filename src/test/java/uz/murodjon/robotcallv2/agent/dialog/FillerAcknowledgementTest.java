package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FillerAcknowledgementTest {

    @Test
    void aBareAcknowledgementAfterTheFillerIsDropped() {
        DialogSession s = mock(DialogSession.class);
        when(s.takeFillerSpoken()).thenReturn(true);

        assertThat(SpeechOutput.withoutBareAcknowledgement(s, "Tushunarli. Qaysi hudud sizga ma'qul?"))
                .isEqualTo("Qaysi hudud sizga ma'qul?");
    }

    @Test
    void anAcknowledgementAboutWhatWasSaidStays() {
        DialogSession s = mock(DialogSession.class);
        when(s.takeFillerSpoken()).thenReturn(true);

        assertThat(SpeechOutput.withoutBareAcknowledgement(s, "Tushunarli, uch xonali. Qaysi hudud?"))
                .isEqualTo("Tushunarli, uch xonali. Qaysi hudud?");
        verify(s, never()).takeFillerSpoken();
    }

    @Test
    void withoutAFillerTheReplyIsUntouched() {
        DialogSession s = mock(DialogSession.class);
        when(s.takeFillerSpoken()).thenReturn(false);

        assertThat(SpeechOutput.withoutBareAcknowledgement(s, "Xo'p. Ertaga qo'ng'iroq qilaman."))
                .isEqualTo("Xo'p. Ertaga qo'ng'iroq qilaman.");
    }

    @Test
    void onlyTheReplysFirstSentenceIsConsidered() {
        DialogSession s = mock(DialogSession.class);
        when(s.spokenText()).thenReturn("Qaysi hudud?");
        when(s.takeFillerSpoken()).thenReturn(true);

        assertThat(SpeechOutput.withoutBareAcknowledgement(s, "Понятно.")).isEqualTo("Понятно.");
    }

    @Test
    void anOpeningThatRepeatsTheBridgeLineIsDropped() {
        DialogSession s = mock(DialogSession.class);
        when(s.takeBridgeLine()).thenReturn("Mandarin House, tushunarli.");

        assertThat(SpeechOutput.withoutBridgeEcho(s, "Tushunarli, Mandarin House majmuasidan qaraymiz."))
                .isEmpty();
    }

    @Test
    void anOpeningEchoingTheBridgeWordsIsDropped() {
        DialogSession s = mock(DialogSession.class);
        when(s.takeBridgeLine()).thenReturn("Ikki xonali, tushundim.");

        assertThat(SpeechOutput.withoutBridgeEcho(s, "Ikki xonali, yaxshi.")).isEmpty();
    }

    @Test
    void aQuestionOrAFigureAfterTheBridgeStays() {
        DialogSession s = mock(DialogSession.class);
        when(s.takeBridgeLine()).thenReturn("Mandarin House, tushunarli.");

        assertThat(SpeechOutput.withoutBridgeEcho(s, "Mandarin House da necha xonali qidiryapsiz?"))
                .isEqualTo("Mandarin House da necha xonali qidiryapsiz?");
        assertThat(SpeechOutput.withoutBridgeEcho(s, "Mandarin House da 3 ta xonadon bor."))
                .isEqualTo("Mandarin House da 3 ta xonadon bor.");
        verify(s, never()).takeBridgeLine();
    }

    @Test
    void withoutABridgeLineTheOpeningStays() {
        DialogSession s = mock(DialogSession.class);

        assertThat(SpeechOutput.withoutBridgeEcho(s, "Tushunarli, Mandarin House majmuasidan qaraymiz."))
                .isEqualTo("Tushunarli, Mandarin House majmuasidan qaraymiz.");
    }
}
