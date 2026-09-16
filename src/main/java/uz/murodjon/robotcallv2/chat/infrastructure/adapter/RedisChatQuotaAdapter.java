package uz.murodjon.robotcallv2.chat.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.chat.application.port.output.ChatQuotaPort;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * An agent's chats of the day in Redis, shared by every instance
 * ({@code chat:daily:<agentId>:<date>}).
 *
 * <p>Check and count are one Lua script, so Redis runs them without another client's command
 * in between: a burst of requests for the last one of the day gets exactly one, where two
 * round trips let all of them see the day free. The day is the agent's calendar day in
 * Tashkent, like every other daily count here, and its key outlives the day so a check just
 * after midnight reads it cleanly.
 */
@Component
public class RedisChatQuotaAdapter implements ChatQuotaPort {

    private static final Logger log = LoggerFactory.getLogger(RedisChatQuotaAdapter.class);

    private static final String DAILY_KEY = "chat:daily:";
    private static final ZoneId ZONE = ZoneId.of("Asia/Tashkent");
    private static final Duration DAILY_KEY_TTL = Duration.ofDays(2);

    /** KEYS: 1 — the agent's day counter. ARGV: 1 — the day's limit, 2 — the key's TTL (s). */
    private static final RedisScript<Long> BOOK_SCRIPT = new DefaultRedisScript<>("""
            if tonumber(redis.call('GET', KEYS[1]) or '0') >= tonumber(ARGV[1]) then
                return 1
            end
            if redis.call('INCR', KEYS[1]) == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisChatQuotaAdapter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean bookSession(long aiAgentId, int dailyLimit) {
        String key = DAILY_KEY + aiAgentId + ":" + LocalDate.now(ZONE);
        try {
            Long answer = redisTemplate.execute(BOOK_SCRIPT, List.of(key),
                    String.valueOf(dailyLimit), String.valueOf(DAILY_KEY_TTL.toSeconds()));
            return answer != null && answer == 0;
        } catch (RuntimeException e) {
            // Refused rather than waved through: this is reachable by anything that can make
            // an HTTP request, and a ceiling that switches itself off during an outage is no
            // ceiling at all.
            log.warn("Chat quota store unreachable, refusing the chat: {}", e.getMessage());
            return false;
        }
    }
}
