package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the caller heard of a turn is what the history, the transcript and the "you were
 * cut off here" note are built from — told it said lines nobody heard, the model never
 * comes back to them.
 */
class SpokenAudioLedgerTest {

    private final RtpEndpoint endpoint = mock(RtpEndpoint.class);
    private final SpokenAudioLedger ledger = new SpokenAudioLedger(endpoint);

    @Test
    void nothingQueuedIsNothingHeard() {
        assertThat(ledger.spokenText()).isNull();
    }

    @Test
    void fullyPlayedLinesAreHeardWhole() {
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        ledger.appendSpokenAudio("to'rt besh", 300, 200);
        when(endpoint.playedSamples()).thenReturn(500L);

        assertThat(ledger.spokenText()).isEqualTo("bir ikki uch to'rt besh");
    }

    @Test
    void lineCutMidwayIsTruncatedAtTheLastWholeWord() {
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        ledger.appendSpokenAudio("to'rt besh", 300, 200);
        // Half of the second line went out: 5 of its 10 characters.
        when(endpoint.playedSamples()).thenReturn(400L);

        assertThat(ledger.spokenText()).isEqualTo("bir ikki uch to'rt");
    }

    @Test
    void cutInsideALineDropsEverythingQueuedBehindIt() {
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        ledger.appendSpokenAudio("to'rt besh", 300, 200);
        when(endpoint.playedSamples()).thenReturn(150L);

        assertThat(ledger.spokenText()).isEqualTo("bir");
    }

    @Test
    void lineThatNeverStartedIsNotHeard() {
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        ledger.appendSpokenAudio("to'rt besh", 300, 200);
        when(endpoint.playedSamples()).thenReturn(300L);

        assertThat(ledger.spokenText()).isEqualTo("bir ikki uch");
    }

    @Test
    void cutBeforeTheFirstWordEndedIsNothingHeard() {
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        when(endpoint.playedSamples()).thenReturn(40L);

        assertThat(ledger.spokenText()).isNull();
    }

    @Test
    void linesWithoutAudioAccountingCountWholeAndComeFirst() {
        ledger.appendSpokenText(" salom ");
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        when(endpoint.playedSamples()).thenReturn(0L);

        assertThat(ledger.spokenText()).isEqualTo("salom");
    }

    @Test
    void withoutAnEndpointEveryLineCountsWhole() {
        SpokenAudioLedger simulated = new SpokenAudioLedger(null);
        simulated.appendSpokenAudio("bir ikki uch", 0, 300);
        simulated.appendSpokenAudio("to'rt besh", 300, 200);

        assertThat(simulated.spokenText()).isEqualTo("bir ikki uch to'rt besh");
    }

    @Test
    void clearingStartsTheNextTurnFromNothing() {
        ledger.appendSpokenText("salom");
        ledger.appendSpokenAudio("bir ikki uch", 0, 300);
        ledger.clearSpokenText();

        assertThat(ledger.spokenText()).isNull();
    }

    @Test
    void unspokenTailIsTakenOnce() {
        ledger.setUnspokenText("  qolgan qismi ");

        assertThat(ledger.hasUnspokenText()).isTrue();
        assertThat(ledger.takeUnspokenText()).isEqualTo("qolgan qismi");
        assertThat(ledger.hasUnspokenText()).isFalse();
        assertThat(ledger.takeUnspokenText()).isNull();
    }
}
