package uz.murodjon.robotcallv2.agent.ari;

import ch.loway.oss.ari4java.generated.models.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The calls this instance holds media for, each with its {@link CallPhase}, and what is
 * known about each channel around them: its hangup cause, whether an operator joined.
 *
 * <p>A phase only changes inside one {@code compute} on the channel's entry, and a session
 * is present exactly while its call is {@link CallPhase#ACTIVE}. That is what makes the
 * teardown happen once: {@link #claim} hands the session to one caller only, however many
 * threads (StasisEnd, the setup thread, the ghost reaper, the shutdown) ask at once.
 */
@Component
class CallSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(CallSessionRegistry.class);

    /** The live calls — present exactly while the channel's phase is {@link CallPhase#ACTIVE}. */
    private final Map<String, CallSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, CallPhase> phases = new ConcurrentHashMap<>();
    /**
     * Q.850 hangup cause per channel, as Asterisk reports it (§8.6). Kept alongside the
     * sessions rather than inside them because the channels that need it most never got a
     * session: an originate that was never answered produces no StasisStart at all, and
     * its cause code is the only thing that distinguishes "line was busy" from "number
     * does not exist".
     */
    private final Map<String, Integer> hangupCauses = new ConcurrentHashMap<>();
    /** Callers bridged to an operator by the legacy transfer — a human call, so the ghost reaper leaves it alone. */
    private final Set<String> operatorJoined = ConcurrentHashMap.newKeySet();
    /**
     * An outcome decided outside any dialog — a progressive call's answering machine found
     * after the operator joined — for the teardown to use when the dialog has none.
     */
    private final Map<String, Disposition> decidedDispositions = new ConcurrentHashMap<>();

    CallSession find(String channelId) {
        return sessions.get(channelId);
    }

    boolean contains(String channelId) {
        return sessions.containsKey(channelId);
    }

    /** A snapshot of the live calls. */
    List<CallSession> findAll() {
        return List.copyOf(sessions.values());
    }

    boolean isEmpty() {
        return sessions.isEmpty();
    }

    boolean isActive(String channelId) {
        return phases.get(channelId) == CallPhase.ACTIVE;
    }

    /** Whether the call is being set up, is live or is ending — anything but unknown here. */
    boolean hasPhase(String channelId) {
        return phases.containsKey(channelId);
    }

    /** Claims the first setup for a channel while its media is being prepared. */
    boolean markSettingUp(String channelId) {
        boolean[] started = new boolean[1];
        phases.compute(channelId, (id, current) -> {
            if (current != null) {
                return current;
            }
            started[0] = true;
            return CallPhase.SETTING_UP;
        });
        return started[0];
    }

    /**
     * StasisEnd: {@link CallPhase#SETTING_UP} → {@link CallPhase#END_REQUESTED}.
     *
     * @return whether the call is still being set up — its setup thread then tears it down,
     *         and the caller has nothing to claim
     */
    boolean requestEnd(String channelId) {
        CallPhase phase = phases.computeIfPresent(channelId,
                (id, current) -> current == CallPhase.SETTING_UP ? CallPhase.END_REQUESTED : current);
        return phase == CallPhase.END_REQUESTED;
    }

    /**
     * The setup is over: a channel it never registered (refused, handled elsewhere, failed)
     * is forgotten. A registered one keeps its phase.
     */
    void finishSettingUp(String channelId) {
        phases.computeIfPresent(channelId, (id, current) ->
                current == CallPhase.SETTING_UP || current == CallPhase.END_REQUESTED ? null : current);
    }

    /**
     * Registers the session and makes its call {@link CallPhase#ACTIVE}.
     *
     * @return false when StasisEnd arrived during the setup ({@link CallPhase#END_REQUESTED}):
     *         the caller is gone and the registering thread must tear the call down itself
     */
    boolean register(CallSession session) {
        boolean[] endRequested = new boolean[1];
        phases.compute(session.channelId(), (id, current) -> {
            endRequested[0] = current == CallPhase.END_REQUESTED;
            sessions.put(id, session);
            return CallPhase.ACTIVE;
        });
        return !endRequested[0];
    }

    /** Replaces the session of a call that is still {@link CallPhase#ACTIVE}; a call already ending is left alone. */
    void update(CallSession session) {
        phases.computeIfPresent(session.channelId(), (id, current) -> {
            if (current == CallPhase.ACTIVE) {
                sessions.put(id, session);
            }
            return current;
        });
    }

    /**
     * {@link CallPhase#ACTIVE} → {@link CallPhase#ENDING}: takes the session out, so whoever
     * gets it back owns its teardown — and only one caller ever does.
     *
     * @return the session, or null when the call is not active (not ours, still being set
     *         up, or already claimed)
     */
    CallSession claim(String channelId) {
        CallSession[] claimed = new CallSession[1];
        phases.computeIfPresent(channelId, (id, current) -> {
            if (current != CallPhase.ACTIVE) {
                return current;
            }
            claimed[0] = sessions.remove(id);
            return CallPhase.ENDING;
        });
        return claimed[0];
    }

    /** The teardown of a claimed call has finished: the channel is forgotten. */
    void finishEnding(String channelId) {
        phases.remove(channelId, CallPhase.ENDING);
    }

    void rememberHangupCause(Channel channel, Integer cause) {
        if (channel == null || cause == null) {
            return;
        }
        Integer previous = hangupCauses.putIfAbsent(channel.getId(), cause);
        if (previous == null) {
            log.debug("Hangup cause for {}: {}", channel.getId(), HangupCause.label(cause));
        }
    }

    Integer findHangupCause(String channelId) {
        return hangupCauses.get(channelId);
    }

    Integer removeHangupCause(String channelId) {
        return hangupCauses.remove(channelId);
    }

    /** Only for a live session: its teardown is what takes the entry back out. */
    void rememberDisposition(String channelId, Disposition disposition) {
        if (sessions.containsKey(channelId)) {
            decidedDispositions.put(channelId, disposition);
        }
    }

    Disposition removeDisposition(String channelId) {
        return decidedDispositions.remove(channelId);
    }

    void markOperatorJoined(String channelId) {
        operatorJoined.add(channelId);
    }

    boolean isOperatorJoined(String channelId) {
        return operatorJoined.contains(channelId);
    }

    void forgetOperatorJoined(String channelId) {
        operatorJoined.remove(channelId);
    }
}
