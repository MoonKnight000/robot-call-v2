package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * A cascade call can be closed from several places at once — a closing turn, the silence
 * watchdog, a dead synthesizer. Only one of them may hang up (or transfer): a second run
 * would drop a line that has already been handed to an operator.
 */
class SpeechOutputFinishTest {

    /** Not playing anything, so there is no audio to wait for. */
    private final RtpEndpoint endpoint = mock(RtpEndpoint.class);
    private final AtomicInteger hangups = new AtomicInteger();
    private final AtomicInteger transfers = new AtomicInteger();
    private SpeechOutput speech;
    private DialogSession session;

    @BeforeEach
    void setUp() {
        DialogPolicy dialogPolicy = new DialogPolicy(null, new DialogTuningProperties(0, 0, 0, 0, 0, 1, 0));
        // Only the closing path runs here: synthesis, metrics and executors are never reached.
        speech = new SpeechOutput(null, null, null, null, null, null, dialogPolicy);
        session = DialogSession.builder("chan-1", "uz-UZ")
                .context(ScenarioFixtures.fullContext())
                .scenario(ScenarioFixtures.debtCollection())
                .endpoint(endpoint)
                .hangup(hangups::incrementAndGet)
                .transfer(transfers::incrementAndGet)
                .callAttemptId(42L)
                .companyId(1L)
                .companyName("Uysot")
                .build();
    }

    @Test
    void hangsUpOnceHoweverManyPathsCloseTheCall() {
        session.end(Disposition.HUNG_UP);

        speech.finishWhenSpoken(session);
        speech.finishWhenSpoken(session);

        assertThat(hangups).hasValue(1);
        assertThat(transfers).hasValue(0);
    }

    @Test
    void transfersOnceAndNeverHangsUpATransferredCall() {
        session.end(Disposition.TRANSFERRED);

        speech.finishWhenSpoken(session);
        speech.finishWhenSpoken(session);

        assertThat(transfers).hasValue(1);
        assertThat(hangups).hasValue(0);
    }

    @Test
    void theClosingClaimGoesToExactlyOneCaller() {
        assertThat(session.beginClosing()).isTrue();
        assertThat(session.beginClosing()).isFalse();
    }
}
