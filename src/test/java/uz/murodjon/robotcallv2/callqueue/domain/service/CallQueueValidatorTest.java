package uz.murodjon.robotcallv2.callqueue.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueue;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.callqueue.domain.enums.QueueOverflowAction;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CallQueueValidatorTest {

    private static CallQueue queue(int ringTimeoutSec, QueueOverflowAction overflow, String target,
                                   int periodicEverySec, String periodicText, List<CallQueueMember> members) {
        return new CallQueue(0, 1, "Sotuv", CallQueueStrategy.LEAST_RECENT, ringTimeoutSec, 10, 300, 0, 60, true,
                periodicText, periodicEverySec, null, 0, null, overflow, target, 20, 0, false, null, false, true,
                members, null);
    }

    private static List<CallQueueMember> twoOperators() {
        return List.of(new CallQueueMember(2, 0, Set.of("uz-UZ")), new CallQueueMember(3, 5, Set.of("ru-RU")));
    }

    @Test
    void aSensibleQueuePasses() {
        assertThatCode(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.CALLBACK, null, 0, null, twoOperators()))).doesNotThrowAnyException();
    }

    @Test
    void ringTimeoutMustBeReasonable() {
        assertThatThrownBy(() -> CallQueueValidator.validate(
                queue(2, QueueOverflowAction.CALLBACK, null, 0, null, twoOperators())))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ringTimeoutSec");
    }

    @Test
    void overflowToAQueueNeedsItsId() {
        assertThatThrownBy(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.QUEUE, null, 0, null, twoOperators())))
                .hasMessageContaining("overflowTarget");
    }

    @Test
    void overflowToANumberNeedsANumber() {
        assertThatThrownBy(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.EXTERNAL_NUMBER, "abc", 0, null, twoOperators())))
                .hasMessageContaining("phone number");
        assertThatCode(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.EXTERNAL_NUMBER, "+998901234567", 0, null, twoOperators())))
                .doesNotThrowAnyException();
    }

    @Test
    void aMemberIsListedOnce() {
        List<CallQueueMember> twice = List.of(new CallQueueMember(2, 0, Set.of()), new CallQueueMember(2, 1, Set.of()));

        assertThatThrownBy(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.CALLBACK, null, 0, null, twice)))
                .hasMessageContaining("listed twice");
    }

    @Test
    void aPeriodicAnnouncementNeedsWords() {
        assertThatThrownBy(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.CALLBACK, null, 120, " ", twoOperators())))
                .hasMessageContaining("periodicAnnounceText");
    }

    @Test
    void skillsAreTags() {
        List<CallQueueMember> spaced = List.of(new CallQueueMember(2, 0, Set.of("uz UZ")));

        assertThatThrownBy(() -> CallQueueValidator.validate(
                queue(15, QueueOverflowAction.CALLBACK, null, 0, null, spaced)))
                .hasMessageContaining("skill");
    }
}
