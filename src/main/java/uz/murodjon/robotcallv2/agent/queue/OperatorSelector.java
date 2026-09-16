package uz.murodjon.robotcallv2.agent.queue;

import uz.murodjon.robotcallv2.callqueue.domain.entity.CallQueueMember;
import uz.murodjon.robotcallv2.callqueue.domain.enums.CallQueueStrategy;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorPresence;
import uz.murodjon.robotcallv2.operator.domain.enums.OperatorStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Which available operators a waiting call is offered to, in order (docs/INBOUND-PBX-PLAN.md
 * §7.3). Pure: presence, eligibility and randomness are handed in, so every strategy is a
 * unit test. The dispatcher rings the first one it can claim — or all of them for RING_ALL.
 *
 * <p>Priority always comes first: a strategy only orders members within the same priority.
 */
public final class OperatorSelector {

    /** How long SKILL_BASED holds out for an operator with the caller's language. */
    static final long SKILL_WAIT_SECONDS = 30;

    private OperatorSelector() {
    }

    /**
     * @param presence        each member's live presence
     * @param eligible        the rest of what "can take a call" means — on shift, softphone online
     * @param language        the call's language, for SKILL_BASED
     * @param waitedSeconds   how long the caller has waited so far
     * @param lastOfferedUser whom this queue offered its previous call to, for ROUND_ROBIN
     */
    public static List<Long> order(CallQueueStrategy strategy, List<CallQueueMember> members,
                                   Function<Long, OperatorPresence> presence, Predicate<Long> eligible,
                                   String language, long waitedSeconds, Long lastOfferedUser, Random random) {
        List<CallQueueMember> available = new ArrayList<>();
        for (CallQueueMember member : members) {
            OperatorPresence state = presence.apply(member.userId());
            if (state != null && state.status() == OperatorStatus.AVAILABLE && eligible.test(member.userId())) {
                available.add(member);
            }
        }
        Comparator<CallQueueMember> byPriority = Comparator.comparingInt(CallQueueMember::priority);
        Comparator<CallQueueMember> leastRecent = Comparator.comparing(
                m -> lastCallEnded(presence.apply(m.userId())), Comparator.nullsFirst(Comparator.naturalOrder()));
        switch (strategy) {
            case RING_ALL, LEAST_RECENT -> available.sort(byPriority.thenComparing(leastRecent));
            case FEWEST_CALLS -> available.sort(byPriority
                    .thenComparingInt((CallQueueMember m) -> presence.apply(m.userId()).callsToday())
                    .thenComparing(leastRecent));
            case ROUND_ROBIN -> {
                rotateAfter(available, members, lastOfferedUser);
                available.sort(byPriority);
            }
            case RANDOM -> {
                Collections.shuffle(available, random);
                available.sort(byPriority);
            }
            case SKILL_BASED -> {
                boolean anyoneSkilled = members.stream().anyMatch(m -> m.hasSkill(language));
                if (anyoneSkilled && waitedSeconds < SKILL_WAIT_SECONDS) {
                    available.removeIf(m -> !m.hasSkill(language));
                }
                available.sort(Comparator.comparing((CallQueueMember m) -> !m.hasSkill(language))
                        .thenComparing(byPriority).thenComparing(leastRecent));
            }
        }
        return available.stream().map(CallQueueMember::userId).toList();
    }

    /** Puts the member after {@code lastOfferedUser} (in queue order) first, wrapping around. */
    private static void rotateAfter(List<CallQueueMember> available, List<CallQueueMember> members, Long lastOfferedUser) {
        if (lastOfferedUser == null || available.isEmpty()) {
            return;
        }
        List<Long> order = members.stream().map(CallQueueMember::userId).toList();
        int last = order.indexOf(lastOfferedUser);
        available.sort(Comparator.comparingInt(m -> {
            int index = order.indexOf(m.userId());
            return Math.floorMod(index - last - 1, order.size());
        }));
    }

    private static Instant lastCallEnded(OperatorPresence presence) {
        return presence != null ? presence.lastCallEndedAt() : null;
    }
}
