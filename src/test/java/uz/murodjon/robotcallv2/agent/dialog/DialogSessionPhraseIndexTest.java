package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fillers and backchannels rotate by how many the call has had, not by turn number: a filler
 * skips quick turns, and indexing by turn gave turns 2 and 4 the same line of a two-line pool.
 */
class DialogSessionPhraseIndexTest {

    @Test
    void fillerAndBackchannelCountersRunOnTheirOwnFromZero() {
        DialogSession session = DialogSession.builder("chan-1", "uz-UZ")
                .context(ScenarioFixtures.fullContext())
                .scenario(ScenarioFixtures.debtCollection())
                .build();

        assertThat(session.takeFillerIndex()).isZero();
        assertThat(session.takeFillerIndex()).isEqualTo(1);
        assertThat(session.takeBackchannelIndex()).isZero();
        assertThat(session.takeFillerIndex()).isEqualTo(2);
        assertThat(session.takeBackchannelIndex()).isEqualTo(1);
    }
}
