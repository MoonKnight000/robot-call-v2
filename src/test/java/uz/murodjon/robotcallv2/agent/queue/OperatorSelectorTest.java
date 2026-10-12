package uz.murodjon.robotcallv2.agent.queue;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorSelectorTest {

    private static final Instant NOW = Instant.parse("2026-09-14T06:00:00Z");

    private final Map<Long, OperatorPresence> presence = new HashMap<>();

    private void operator(long userId, OperatorStatus status, Instant lastCallEnded, int callsToday) {
        presence.put(userId, new OperatorPresence(userId, 1, status, null, NOW, null, lastCallEnded, callsToday, 0, null));
    }

    private List<Long> order(CallQueueStrategy strategy, List<CallQueueMember> members, String language, long waited,
                             Long lastOffered) {
        return OperatorSelector.order(strategy, members, presence::get, userId -> true, language, waited, lastOffered,
                new Random(7));
    }

    private static CallQueueMember member(long userId) {
        return new CallQueueMember(userId, 0, Set.of());
    }

    @Test
    void onlyAvailableAndEligibleOperatorsAreOffered() {
        operator(1, OperatorStatus.AVAILABLE, null, 0);
        operator(2, OperatorStatus.ON_CALL, null, 0);
        operator(3, OperatorStatus.BREAK, null, 0);
        operator(4, OperatorStatus.AVAILABLE, null, 0);

        List<Long> ordered = OperatorSelector.order(CallQueueStrategy.RING_ALL,
                List.of(member(1), member(2), member(3), member(4)), presence::get, userId -> userId != 4,
                "uz-UZ", 0, null, new Random(1));

        assertThat(ordered).containsExactly(1L);
    }

    @Test
    void leastRecentOffersWhoeverHasBeenFreeTheLongest() {
        operator(1, OperatorStatus.AVAILABLE, NOW.minusSeconds(60), 0);
        operator(2, OperatorStatus.AVAILABLE, NOW.minusSeconds(600), 0);
        operator(3, OperatorStatus.AVAILABLE, null, 0);

        assertThat(order(CallQueueStrategy.LEAST_RECENT, List.of(member(1), member(2), member(3)), "uz-UZ", 0, null))
                .containsExactly(3L, 2L, 1L);
    }

    @Test
    void fewestCallsOffersTheLeastBusy() {
        operator(1, OperatorStatus.AVAILABLE, null, 9);
        operator(2, OperatorStatus.AVAILABLE, null, 2);

        assertThat(order(CallQueueStrategy.FEWEST_CALLS, List.of(member(1), member(2)), "uz-UZ", 0, null))
                .containsExactly(2L, 1L);
    }

    @Test
    void priorityComesBeforeTheStrategy() {
        operator(1, OperatorStatus.AVAILABLE, null, 0);
        operator(2, OperatorStatus.AVAILABLE, NOW.minusSeconds(60), 0);

        List<CallQueueMember> members = List.of(new CallQueueMember(1, 10, Set.of()), new CallQueueMember(2, 0, Set.of()));

        assertThat(order(CallQueueStrategy.LEAST_RECENT, members, "uz-UZ", 0, null)).containsExactly(2L, 1L);
    }

    @Test
    void roundRobinContinuesAfterTheLastOffered() {
        operator(1, OperatorStatus.AVAILABLE, null, 0);
        operator(2, OperatorStatus.AVAILABLE, null, 0);
        operator(3, OperatorStatus.AVAILABLE, null, 0);

        assertThat(order(CallQueueStrategy.ROUND_ROBIN, List.of(member(1), member(2), member(3)), "uz-UZ", 0, 2L))
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void skillBasedHoldsOutForTheCallersLanguageThenTakesAnyone() {
        operator(1, OperatorStatus.AVAILABLE, null, 0);
        operator(2, OperatorStatus.AVAILABLE, null, 0);
        List<CallQueueMember> members = List.of(new CallQueueMember(1, 0, Set.of("ru-RU")),
                new CallQueueMember(2, 0, Set.of("uz-UZ")));

        assertThat(order(CallQueueStrategy.SKILL_BASED, members, "uz-UZ", 5, null)).containsExactly(2L);
        assertThat(order(CallQueueStrategy.SKILL_BASED, members, "uz-UZ", 45, null)).containsExactly(2L, 1L);
    }

    @Test
    void skillBasedWithNobodySkilledTakesEveryone() {
        operator(1, OperatorStatus.AVAILABLE, null, 0);
        operator(2, OperatorStatus.AVAILABLE, null, 0);

        assertThat(order(CallQueueStrategy.SKILL_BASED, List.of(member(1), member(2)), "uz-UZ", 0, null))
                .containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void randomStillOffersEveryAvailableOperator() {
        operator(1, OperatorStatus.AVAILABLE, null, 0);
        operator(2, OperatorStatus.AVAILABLE, null, 0);
        operator(3, OperatorStatus.AVAILABLE, null, 0);

        assertThat(order(CallQueueStrategy.RANDOM, List.of(member(1), member(2), member(3)), "uz-UZ", 0, null))
                .containsExactlyInAnyOrder(1L, 2L, 3L);
    }
}
