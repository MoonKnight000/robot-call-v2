package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.dialer.application.port.input.DialerSlotUseCase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the number of active/reserved outbound calls to enforce
 * max_concurrent_calls (PROJECT.md §10). A slot is reserved at dispatch and
 * released exactly once when the call ends (or is reclaimed).
 *
 * <p>Counted <b>per company as well as in total</b>. Data isolation (ROADMAP B) does not
 * help a tenant whose calls never get placed: a single counter meant one company's 50 000
 * target campaign held every slot on the platform, and every other company's campaign sat
 * ACTIVE and dialled nothing. The per-company count is what a company's own limit is
 * measured against; the total is what the machine's RTP port range is measured against
 * ({@code RtpProperties#mediaCapacity()}), so per-company limits added up can never
 * promise more media than exists.
 *
 * <p>With several instances (each with its own Asterisk) the total is the cluster's, so
 * the media it is measured against is the cluster's too: every instance announces its own
 * port capacity ({@link #announceCapacity}), and {@link #countClusterCapacity} adds up the
 * ones heard from recently.
 */
@Component
public class DialerState implements DialerSlotUseCase {

    private static final Logger log = LoggerFactory.getLogger(DialerState.class);

    private static final String TOTAL_KEY = "dialer:active";
    private static final String COMPANY_KEY_PREFIX = "dialer:active:company:";
    private static final String CAMPAIGN_KEY_PREFIX = "dialer:active:campaign:";

    private static final Duration TTL = Duration.ofHours(6);
    private static final Duration DAILY_TTL = Duration.ofDays(2);

    /** Hash of instance id → {@code capacity:announcedAtMillis}. */
    private static final String CAPACITY_KEY = "dialer:media:capacity";
    /** Three missed announcements: past this an instance is taken to be gone. */
    private static final Duration CAPACITY_STALE_AFTER = Duration.ofSeconds(30);
    /** Set of target ids per instance; outlives a few missed announcements, not a crash. */
    private static final String LIVE_TARGETS_KEY_PREFIX = "dialer:live-targets:";
    private static final Duration LIVE_TARGETS_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;
    private final AtomicInteger localTotal = new AtomicInteger(0);
    /** The name calls are routed to this instance by — one name per instance, everywhere. */
    private final String instanceId;

    public DialerState(StringRedisTemplate redisTemplate, CallRouteRegistry callRouteRegistry) {
        this.redisTemplate = redisTemplate;
        this.instanceId = callRouteRegistry.instanceId();
    }

    /** Tell the cluster how many calls this instance can carry media for; 0 while it drains. */
    public void announceCapacity(int capacity) {
        try {
            redisTemplate.opsForHash().put(CAPACITY_KEY, instanceId,
                    capacity + ":" + System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("Media capacity not announced to Redis: {}", e.getMessage());
        }
    }

    /**
     * Tell the cluster which targets this instance has a call ringing or on the line for.
     * Replaced whole each time and left to expire, so a crashed instance's calls stop
     * counting as live once nobody refreshes them.
     */
    public void announceLiveTargets(Set<Long> targetIds) {
        String key = LIVE_TARGETS_KEY_PREFIX + instanceId;
        try {
            if (targetIds.isEmpty()) {
                redisTemplate.delete(key);
                return;
            }
            // Built aside and renamed into place: a reader between a delete and the add
            // would see this instance's calls as none.
            String next = key + ":next";
            redisTemplate.delete(next);
            redisTemplate.opsForSet().add(next, targetIds.stream().map(String::valueOf).toArray(String[]::new));
            redisTemplate.expire(next, LIVE_TARGETS_TTL);
            redisTemplate.rename(next, key);
        } catch (Exception e) {
            log.warn("Live targets not announced to Redis: {}", e.getMessage());
        }
    }

    /**
     * Targets with a call live on any instance of the cluster ({@link #announceLiveTargets}).
     * Empty when Redis cannot be read — not "none", since settling a live call as failed is
     * the mistake this exists to prevent.
     */
    public Optional<Set<Long>> findClusterLiveTargetIds() {
        try {
            Set<Long> live = new HashSet<>();
            for (Object instance : redisTemplate.opsForHash().keys(CAPACITY_KEY)) {
                Set<String> members = redisTemplate.opsForSet().members(LIVE_TARGETS_KEY_PREFIX + instance);
                if (members != null) {
                    members.forEach(member -> live.add(Long.parseLong(member)));
                }
            }
            return Optional.of(live);
        } catch (Exception e) {
            log.warn("Cluster live targets unavailable: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Calls the instances heard from in the last {@link #CAPACITY_STALE_AFTER} can carry
     * media for between them. Instances gone quiet are dropped from the hash on the way.
     *
     * @param localCapacity this instance's own — the answer when nobody has announced yet
     *                      (the first tick after start) or Redis cannot be read
     */
    public int countClusterCapacity(int localCapacity) {
        try {
            long cutoff = System.currentTimeMillis() - CAPACITY_STALE_AFTER.toMillis();
            int total = 0;
            boolean heard = false;
            for (Map.Entry<Object, Object> entry : redisTemplate.opsForHash().entries(CAPACITY_KEY).entrySet()) {
                String[] parts = String.valueOf(entry.getValue()).split(":");
                long announcedAt = parts.length == 2 ? Long.parseLong(parts[1]) : 0;
                if (announcedAt < cutoff) {
                    redisTemplate.opsForHash().delete(CAPACITY_KEY, entry.getKey());
                    continue;
                }
                total += Integer.parseInt(parts[0]);
                heard = true;
            }
            return heard ? total : localCapacity;
        } catch (Exception e) {
            log.warn("Cluster media capacity unavailable, using this instance's own: {}", e.getMessage());
            return localCapacity;
        }
    }

    /** Calls this company is holding right now. */
    public int countActive(long companyId) {
        return count(companyKey(companyId));
    }

    /** Calls the whole platform is holding right now — the figure the port range caps. */
    public int countActiveTotal() {
        return count(TOTAL_KEY);
    }

    /** Calls this campaign is holding right now — what its own maxConcurrentCalls is measured against. */
    public int countActiveForCampaign(long campaignId) {
        return count(campaignKey(campaignId));
    }

    public void reserve(long companyId, long campaignId) {
        localTotal.incrementAndGet();
        increment(TOTAL_KEY);
        increment(companyKey(companyId));
        increment(campaignKey(campaignId));
    }

    @Override
    public void release(long companyId, long campaignId) {
        localTotal.updateAndGet(n -> n > 0 ? n - 1 : 0);
        decrement(TOTAL_KEY);
        decrement(companyKey(companyId));
        decrement(campaignKey(campaignId));
    }

    private int count(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            return value == null ? 0 : Math.max(0, Integer.parseInt(value));
        } catch (Exception e) {
            log.warn("Redis unavailable for the concurrency counter, falling back to the local one: {}",
                    e.getMessage());
            // Only the total has a local mirror. A per-company figure held in one JVM's
            // memory would be wrong the moment a second instance runs, and a limit that is
            // wrong per company is worse than one that falls back to the platform's.
            return localTotal.get();
        }
    }

    private void increment(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            // The TTL is the safety net for a counter whose release never came (a crash
            // between dispatch and originate). Set when the key is born, not on every
            // reserve — refreshing it under steady traffic kept a leaked count alive for ever.
            if (count != null && count == 1) {
                redisTemplate.expire(key, TTL);
            }
        } catch (Exception e) {
            log.warn("Concurrency reserve not recorded in Redis for {}: {}", key, e.getMessage());
        }
    }

    private void decrement(String key) {
        try {
            Long remaining = redisTemplate.opsForValue().decrement(key);
            if (remaining != null && remaining < 0) {
                redisTemplate.opsForValue().set(key, "0");
            }
        } catch (Exception e) {
            log.warn("Concurrency release not recorded in Redis for {}: {}", key, e.getMessage());
        }
    }

    private static String companyKey(long companyId) {
        return COMPANY_KEY_PREFIX + companyId;
    }

    private static String campaignKey(long campaignId) {
        return CAMPAIGN_KEY_PREFIX + campaignId;
    }

    public void countDispatch(long campaignId, LocalDate day) {
        String key = dailyKey(campaignId, day);
        try {
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, DAILY_TTL);
        } catch (Exception e) {
            log.warn("Daily dispatch count not recorded for campaign {}: {}", campaignId, e.getMessage());
        }
    }

    /**
     * Calls this campaign dispatched on {@code day}; empty when the count cannot be read,
     * so the caller can refuse to dial rather than read an outage as "none yet".
     */
    public OptionalInt countDispatchedToday(long campaignId, LocalDate day) {
        try {
            String value = redisTemplate.opsForValue().get(dailyKey(campaignId, day));
            return OptionalInt.of(value == null ? 0 : Math.max(0, Integer.parseInt(value)));
        } catch (Exception e) {
            log.debug("Daily dispatch count unavailable for campaign {}: {}", campaignId, e.getMessage());
            return OptionalInt.empty();
        }
    }

    private static String dailyKey(long campaignId, LocalDate day) {
        return "dialer:campaign:" + campaignId + ":" + day;
    }
}
