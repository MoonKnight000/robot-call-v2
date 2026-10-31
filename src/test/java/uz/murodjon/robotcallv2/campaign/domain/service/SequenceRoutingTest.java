package uz.murodjon.robotcallv2.campaign.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.campaign.domain.enums.SequenceOutcome;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import static org.assertj.core.api.Assertions.assertThat;

class SequenceRoutingTest {

    @Test
    void everyDispositionRoutesOnSomeOutcome() {
        for (Disposition disposition : Disposition.values()) {
            assertThat(SequenceRouting.outcomeOf(disposition)).isNotNull();
        }
    }

    @Test
    void aConversionIsASuccessAndARefusalIsNotInterested() {
        assertThat(SequenceRouting.outcomeOf(Disposition.PROMISE_TO_PAY)).isEqualTo(SequenceOutcome.SUCCESS);
        assertThat(SequenceRouting.outcomeOf(Disposition.REFUSED)).isEqualTo(SequenceOutcome.NOT_INTERESTED);
        assertThat(SequenceRouting.outcomeOf(Disposition.DO_NOT_CALL)).isEqualTo(SequenceOutcome.DO_NOT_CALL);
    }

    @Test
    void callsThatNeverReachedTheContactAreRetriedNotFinal() {
        assertThat(SequenceRouting.isFinal(Disposition.NO_ANSWER)).isFalse();
        assertThat(SequenceRouting.isFinal(Disposition.VOICEMAIL)).isFalse();
        assertThat(SequenceRouting.isFinal(Disposition.CALLBACK_REQUESTED)).isFalse();
        assertThat(SequenceRouting.isFinal(Disposition.PROMISE_TO_PAY)).isTrue();
    }

    @Test
    void aWrongNumberAndADoNotCallNeverGoOnToAnotherStep() {
        assertThat(SequenceRouting.endsSequence(Disposition.WRONG_NUMBER)).isTrue();
        assertThat(SequenceRouting.endsSequence(Disposition.DO_NOT_CALL)).isTrue();
        assertThat(SequenceRouting.endsSequence(Disposition.REFUSED)).isFalse();
    }
}
