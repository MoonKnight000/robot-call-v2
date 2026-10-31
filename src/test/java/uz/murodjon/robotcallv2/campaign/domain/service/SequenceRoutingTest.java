package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceRoutingTest {

    @Test
    void outcomesAreJudgedOnlyWhenSomebodyAnswered() {
        assertThat(SequenceRouting.isAnswered(Disposition.NO_ANSWER)).isFalse();
        assertThat(SequenceRouting.isAnswered(Disposition.VOICEMAIL)).isFalse();
        assertThat(SequenceRouting.isAnswered(Disposition.FAILED)).isFalse();
        assertThat(SequenceRouting.isAnswered(Disposition.REFUSED)).isTrue();
        assertThat(SequenceRouting.isAnswered(Disposition.PROMISE_TO_PAY)).isTrue();
    }

    @Test
    void callsThatNeverReachedTheContactAreRetriedNotFinal() {
        assertThat(SequenceRouting.isFinal(Disposition.NO_ANSWER)).isFalse();
        assertThat(SequenceRouting.isFinal(Disposition.VOICEMAIL)).isFalse();
        assertThat(SequenceRouting.isFinal(Disposition.CALLBACK_REQUESTED)).isFalse();
        assertThat(SequenceRouting.isFinal(Disposition.PROMISE_TO_PAY)).isTrue();
    }

    @Test
    void anUnusableNumberAndADoNotCallNeverGoOnToAnotherStep() {
        assertThat(SequenceRouting.endsSequence(Disposition.WRONG_NUMBER)).isTrue();
        assertThat(SequenceRouting.endsSequence(Disposition.DO_NOT_CALL)).isTrue();
        assertThat(SequenceRouting.endsSequence(Disposition.CARRIER_REJECTED)).isTrue();
        assertThat(SequenceRouting.endsSequence(Disposition.REFUSED)).isFalse();
    }
}
