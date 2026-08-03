package uz.murodjon.uysotvoice.agent.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Two-level cache of synthesized audio, in front of the TTS providers (PROJECT.md §2.5).
 *
 * <p>Yandex bills SpeechKit per character, and a debt-collection call repeats itself by
 * design: the disclosure, the goodbye, "say that again", and most of the model's short
 * confirmations are byte-identical from call to call. Level 1 is a per-process LRU —
 * it also takes a whole synthesis round trip out of a live turn. Level 2 is Redis, so
 * a line paid for on one instance is free on every other one and survives a restart;
 * without it the same handful of phrases is re-bought after every deploy.
 *
 * <p>Redis is best-effort throughout: an outage costs cache hits, never a call.
 */
@Component
public class TtsCache {

    private static final Logger log = LoggerFactory.getLogger(TtsCache.class);

    /** Key namespace, versioned so a change to the stored PCM layout strands old entries. */
    private static final String KEY_PREFIX = "tts:v1:";

    /** Used when {@code max-chars} is absent, so a half-filled cache block is not a dead cache. */
    private static final int DEFAULT_MAX_CHARS = 200;

    private final TtsCacheProperties props;
    private final int maxChars;

    /**
     * Short hash of the voice settings the stored audio was produced with. Without it,
     * switching a voice (or the sample rate) would keep serving the old voice out of
     * Redis for the whole TTL, and the change would look like it silently did nothing.
     */
    private final String voiceFingerprint;

    /**
     * Recently synthesized short lines, access-ordered = LRU. Synchronized because
     * turns from different calls hit it concurrently.
     */
    private final Map<String, short[]> memory;

    /** Null when Redis is not configured or the persistent layer is switched off. */
    private final RedisTemplate<String, byte[]> redis;

    /** Set after the first Redis failure so an outage is reported once, not per turn. */
    private volatile boolean redisFailureLogged;

    public TtsCache(TtsProperties ttsProps, ObjectProvider<RedisConnectionFactory> connectionFactory) {
        this.props = ttsProps.cache() != null ? ttsProps.cache() : TtsCacheProperties.disabled();
        int capacity = props.size();
        this.memory = capacity > 0
                ? Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, short[]> eldest) {
                        return size() > capacity;
                    }
                })
                : null;
        this.maxChars = props.maxChars() > 0 ? props.maxChars() : DEFAULT_MAX_CHARS;
        this.voiceFingerprint = fingerprint(ttsProps);
        this.redis = props.redis() ? buildTemplate(connectionFactory.getIfAvailable()) : null;
        log.info("TTS cache: memory={}, redis={}, maxChars={}, voices={}",
                capacity > 0 ? capacity + " entries" : "off",
                redis != null ? "on (ttl " + props.ttlDays() + "d)" : "off",
                maxChars, voiceFingerprint);
    }

    public boolean prewarmEnabled() {
        return props.prewarm() && (memory != null || redis != null);
    }

    /**
     * Cached audio for this line, or {@code null} for a miss (including lines that are
     * not worth caching at all).
     */
    public short[] get(String provider, String language, String voice, String text, EffectiveVoiceSettings style) {
        String key = key(provider, language, voice, text, style);
        if (key == null) {
            return null;
        }
        if (memory != null) {
            short[] hit = memory.get(key);
            if (hit != null) {
                return hit;
            }
        }
        byte[] stored = readRedis(key);
        if (stored == null) {
            return null;
        }
        short[] pcm = toPcm(stored);
        if (memory != null) {
            memory.put(key, pcm); // promote so the next hit does not go to Redis
        }
        return pcm;
    }

    /** Store freshly synthesized audio in both levels. */
    public void put(String provider, String language, String voice, String text, short[] pcm,
                    EffectiveVoiceSettings style) {
        String key = key(provider, language, voice, text, style);
        if (key == null || pcm == null || pcm.length == 0) {
            return;
        }
        if (memory != null) {
            memory.put(key, pcm);
        }
        writeRedis(key, pcm);
    }

    /**
     * Cache key, or {@code null} when this request is not worth caching.
     *
     * <p>The text is normalized (trimmed, runs of whitespace collapsed) before hashing:
     * the model emits the same sentence with a stray leading space or a line break often
     * enough that keying on the raw string quietly halves the hit rate. Case and
     * punctuation are left alone — both change how the line is spoken, so they are part
     * of the identity of the audio, not noise.
     *
     * <p>{@code voice} is the campaign's chosen voice, blank when the provider's own
     * configured voice was used. It is part of the key for the same reason the
     * fingerprint is: two campaigns speaking the same disclosure in different voices
     * must not be served each other's audio.
     *
     * <p>{@code style} (§11 settings/voice) is folded in only when a company actually
     * overrode speed/pitch — leaving the key unchanged for the common case keeps every
     * entry cached before this feature existed valid, and a company with no override
     * shares the default-routing cache instead of never hitting it.
     */
    private String key(String provider, String language, String voice, String text, EffectiveVoiceSettings style) {
        if ((memory == null && redis == null) || text == null) {
            return null;
        }
        String normalized = normalize(text);
        // Above this length a line is unlikely to ever repeat, so caching it only
        // spends memory (and Redis) on a single use.
        if (normalized.isEmpty() || normalized.length() > maxChars) {
            return null;
        }
        String voiceSegment = (voice == null || voice.isBlank()) ? "-" : voice;
        String styleSegment = (style == null || (style.speed() == null && style.pitch() == null))
                ? "" : ":" + style.speed() + "/" + style.pitch();
        return KEY_PREFIX + voiceFingerprint + ':' + provider + ':' + language + ':'
                + voiceSegment + styleSegment + ':' + sha256(normalized);
    }

    /**
     * Everything that changes how a line sounds, hashed into eight characters. Both
     * providers are folded into one fingerprint: a voice change is rare, and having it
     * strand a few of the other provider's entries costs one re-synthesis each.
     */
    private static String fingerprint(TtsProperties props) {
        StringBuilder sb = new StringBuilder();
        YandexTtsProperties yandex = props.yandex();
        if (yandex != null) {
            sb.append(yandex.voice()).append('|').append(sorted(yandex.voices()))
                    .append('|').append(yandex.emotion()).append('|').append(yandex.sampleRate());
        }
        sb.append("//");
        GoogleTtsProperties google = props.google();
        if (google != null) {
            sb.append(sorted(google.voices())).append('|').append(google.speakingRate())
                    .append('|').append(google.pitch()).append('|').append(google.sampleRate());
        }
        return sha256(sb.toString()).substring(0, 8);
    }

    /** Sorted so the fingerprint depends on the voices, not on map iteration order. */
    private static String sorted(Map<String, String> voices) {
        return voices == null ? "{}" : new TreeMap<>(voices).toString();
    }

    static String normalize(String text) {
        return text.strip().replaceAll("\\s+", " ");
    }

    private byte[] readRedis(String key) {
        if (redis == null) {
            return null;
        }
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            reportRedisFailure("read", e);
            return null;
        }
    }

    private void writeRedis(String key, short[] pcm) {
        if (redis == null) {
            return;
        }
        try {
            // Redis rejects a zero expiry, and a misconfigured 0 must not take the
            // whole persistent layer down with it.
            redis.opsForValue().set(key, toBytes(pcm), Duration.ofDays(Math.max(1, props.ttlDays())));
        } catch (Exception e) {
            reportRedisFailure("write", e);
        }
    }

    private void reportRedisFailure(String operation, Exception e) {
        if (redisFailureLogged) {
            log.debug("TTS cache Redis {} failed: {}", operation, e.getMessage());
            return;
        }
        redisFailureLogged = true;
        log.warn("TTS cache Redis {} failed ({}); serving from memory only until it recovers",
                operation, e.getMessage());
    }

    private static RedisTemplate<String, byte[]> buildTemplate(RedisConnectionFactory connectionFactory) {
        if (connectionFactory == null) {
            log.warn("TTS cache: persistent layer requested but no Redis connection factory is available");
            return null;
        }
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    /** 16-bit little-endian, the layout every provider here already produces. */
    private static byte[] toBytes(short[] pcm) {
        ByteBuffer buffer = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.asShortBuffer().put(pcm);
        return buffer.array();
    }

    private static short[] toPcm(byte[] bytes) {
        short[] pcm = new short[bytes.length / 2];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
        return pcm;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required for TTS cache keys", e);
        }
    }
}
