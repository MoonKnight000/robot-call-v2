package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.dialer.application.port.input.TrunkCapacityUseCase;
import uz.murodjon.robotcallv2.dialer.application.dto.TrunkCapacityRow;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;
import uz.murodjon.robotcallv2.siptrunk.application.port.input.SipTrunkUseCase;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Learns how many calls each SIP trunk carries at once, kept in Redis so every instance
 * adds to the same picture ({@link TrunkCapacityRow}).
 *
 * <p>Only the dialer's own calls are counted ({@link DialerState#countActiveForTrunk}):
 * inbound and manual calls on the same trunk take channels too, so on a trunk that also
 * carries those the provider will refuse earlier than these numbers suggest.
 */
@Component
public class TrunkCapacityTracker implements TrunkCapacityUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrunkCapacityTracker.class);

    /**
     * Q.850 causes a provider answers with when it has no channel left: 34 no circuit
     * available (Asterisk's reading of SIP 503), 42 switching equipment congestion,
     * 44 requested channel unavailable, 47 resource unavailable.
     */
    private static final Set<Integer> NO_CHANNEL_CAUSES = Set.of(34, 42, 44, 47);

    private static final String KEY_PREFIX = "dialer:trunk:capacity:";
    private static final String PEAK = "peak";
    private static final String CONGESTION_LIMIT = "congestionLimit";
    private static final String CONGESTION_AT = "congestionAt";
    /** Learned figures go stale when a contract changes; a month without traffic forgets them. */
    private static final Duration TTL = Duration.ofDays(30);

    private final StringRedisTemplate redisTemplate;
    private final DialerState dialerState;
    private final SipTrunkUseCase sipTrunkUseCase;
    private final Clock clock;

    public TrunkCapacityTracker(StringRedisTemplate redisTemplate, DialerState dialerState,
                                SipTrunkUseCase sipTrunkUseCase, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.dialerState = dialerState;
        this.sipTrunkUseCase = sipTrunkUseCase;
        this.clock = clock;
    }

    @Override
    public void recordConnectedCall(Long sipTrunkId) {
        if (sipTrunkId == null) {
            return;
        }
        int active = dialerState.countActiveForTrunk(sipTrunkId);
        try {
            String key = key(sipTrunkId);
            Object stored = redisTemplate.opsForHash().get(key, PEAK);
            // Read-then-write: two instances racing can lose one of two equal-ish peaks,
            // which for a figure that only ever ratchets up costs nothing.
            if (stored == null || Integer.parseInt(stored.toString()) < active) {
                redisTemplate.opsForHash().put(key, PEAK, String.valueOf(active));
            }
            redisTemplate.expire(key, TTL);
        } catch (Exception e) {
            log.debug("Trunk {} peak not recorded: {}", sipTrunkId, e.getMessage());
        }
    }

    @Override
    public void recordUnansweredCall(Long sipTrunkId, Integer hangupCause) {
        if (sipTrunkId == null || hangupCause == null || !NO_CHANNEL_CAUSES.contains(hangupCause)) {
            return;
        }
        // The count still holds the refused call: the provider carried one fewer than that.
        int limit = dialerState.countActiveForTrunk(sipTrunkId) - 1;
        if (limit < 1) {
            // Refused with nothing else up: the trunk is down, not full — TrunkHealth's business.
            return;
        }
        try {
            String key = key(sipTrunkId);
            redisTemplate.opsForHash().putAll(key, Map.of(
                    CONGESTION_LIMIT, String.valueOf(limit),
                    CONGESTION_AT, String.valueOf(clock.millis())));
            redisTemplate.expire(key, TTL);
            log.warn("SIP trunk {} refused a call for want of a channel (cause {}) with {} other call(s) up — "
                    + "its channel count looks like {}", sipTrunkId, hangupCause, limit, limit);
        } catch (Exception e) {
            log.debug("Trunk {} congestion not recorded: {}", sipTrunkId, e.getMessage());
        }
    }

    @Override
    public List<TrunkCapacityRow> findTrunkCapacities(long companyId) {
        return sipTrunkUseCase.findAllEnabledForCompany(companyId).stream().map(this::toTrunkCapacityRow).toList();
    }

    private TrunkCapacityRow toTrunkCapacityRow(SipTrunkRow trunk) {
        long sipTrunkId = trunk.id();
        int active = dialerState.countActiveForTrunk(sipTrunkId);
        int peak = 0;
        Integer congestionLimit = null;
        Instant congestionAt = null;
        try {
            Map<Object, Object> stored = redisTemplate.opsForHash().entries(key(sipTrunkId));
            if (stored.get(PEAK) != null) {
                peak = Integer.parseInt(stored.get(PEAK).toString());
            }
            if (stored.get(CONGESTION_LIMIT) != null) {
                congestionLimit = Integer.parseInt(stored.get(CONGESTION_LIMIT).toString());
                congestionAt = Instant.ofEpochMilli(Long.parseLong(stored.get(CONGESTION_AT).toString()));
            }
        } catch (Exception e) {
            log.debug("Trunk {} capacity unavailable: {}", sipTrunkId, e.getMessage());
        }
        // A refusal says where the ceiling is; the peak says what has already worked, so the
        // suggestion is never below it even if other traffic made one refusal come early.
        Integer suggested = congestionLimit != null ? Math.max(congestionLimit, peak) : null;
        return new TrunkCapacityRow(sipTrunkId, trunk.name(), trunk.maxChannels(), active, peak, congestionLimit,
                congestionAt, suggested);
    }

    private static String key(long sipTrunkId) {
        return KEY_PREFIX + sipTrunkId;
    }
}
