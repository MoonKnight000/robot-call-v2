package uz.murodjon.robotcallv2.contact.domain.service;

import java.time.Duration;
import java.time.Instant;

/** When a failed contact → CRM write is tried again, and when it is given up. */
public final class CrmPushRetryPolicy {

    public static final int MAX_ATTEMPTS = 8;
    private static final Duration FIRST_DELAY = Duration.ofMinutes(1);
    private static final Duration MAX_DELAY = Duration.ofHours(6);

    private CrmPushRetryPolicy() {
    }

    /** After {@code attempts} failures: whether to stop trying. */
    public static boolean isExhausted(int attempts) {
        return attempts >= MAX_ATTEMPTS;
    }

    /** 1, 2, 4, … minutes after the {@code attempts}-th failure, at most six hours. */
    public static Instant findNextAttemptAt(Instant now, int attempts) {
        long minutes = FIRST_DELAY.toMinutes() << Math.min(Math.max(attempts - 1, 0), 20);
        Duration delay = Duration.ofMinutes(minutes);
        return now.plus(delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay);
    }
}
