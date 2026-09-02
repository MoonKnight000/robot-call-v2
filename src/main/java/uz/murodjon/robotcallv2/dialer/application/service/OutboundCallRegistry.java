package uz.murodjon.robotcallv2.dialer.application.service;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks originated outbound calls from origination until the channel ends
 * (PROJECT.md §10). {@link #remove} is the single ownership transfer — whoever
 * removes an entry (teardown on hangup, or the sweeper on an unanswered call)
 * owns releasing its concurrency slot, so a slot is never double-released.
 */
@Component
public class OutboundCallRegistry {

    private static final class Holder {
        final OutboundCall call;
        final Instant originatedAt = Instant.now();
        volatile boolean answered;

        Holder(OutboundCall call) {
            this.call = call;
        }
    }

    private final Map<String, Holder> byChannel = new ConcurrentHashMap<>();

    public void register(String channelId, OutboundCall call) {
        byChannel.put(channelId, new Holder(call));
    }

    /** The call for {@code channelId} without removing it (used to build context on answer). */
    public OutboundCall peek(String channelId) {
        Holder h = byChannel.get(channelId);
        return h != null ? h.call : null;
    }

    /** Mark that the channel entered Stasis (answered), so the sweeper leaves it alone. */
    public void markAnswered(String channelId) {
        Holder h = byChannel.get(channelId);
        if (h != null) {
            h.answered = true;
        }
    }

    /** Remove and return the call, or {@code null} if not an outbound call we track. */
    public OutboundCall remove(String channelId) {
        Holder h = byChannel.remove(channelId);
        return h != null ? h.call : null;
    }

    /**
     * Remove and return the call only if it never answered; {@code null} otherwise.
     */
    public OutboundCall removeIfUnanswered(String channelId) {
        Holder h = byChannel.get(channelId);
        if (h == null || h.answered) {
            return null;
        }
        return byChannel.remove(channelId, h) ? h.call : null;
    }

    /** Channel ids dispatched but never answered within {@code olderThan} (to reclaim). */
    public List<String> staleUnanswered(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Holder> e : byChannel.entrySet()) {
            Holder h = e.getValue();
            if (!h.answered && h.originatedAt.isBefore(cutoff)) {
                stale.add(e.getKey());
            }
        }
        return stale;
    }
}
