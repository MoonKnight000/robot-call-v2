package uz.murodjon.robotcallv2.dialer.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.dialer.application.port.input.TrunkHealthUseCase;
import uz.murodjon.robotcallv2.dialer.application.port.output.TrunkRegistrationStatusPort;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;
import uz.murodjon.robotcallv2.siptrunk.application.dto.SipTrunkRow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Which SIP trunks the dialer should keep sending calls to right now (NEXT-PLAN C.4).
 *
 * <p>Two signals. The registration monitor knows a trunk whose REGISTER the carrier is
 * refusing — every call over it fails before it rings. And the calls themselves: a trunk
 * that answered {@code CARRIER_REJECTED} or {@code FAILED} several times running is
 * rested for a while, so a carrier outage costs a few attempts, not the whole campaign's
 * batch every tick until somebody notices. One success clears the count.
 *
 * <p>Per instance: a second instance learns the same failures from its own calls. That
 * is fine for a safety rest; it is not a shared health registry.
 */
@Component
public class TrunkHealth implements TrunkHealthUseCase {

    private static final Logger log = LoggerFactory.getLogger(TrunkHealth.class);

    /** Consecutive carrier failures before a trunk is rested. */
    static final int FAILURES_BEFORE_REST = 5;
    static final Duration REST = Duration.ofMinutes(5);

    private final TrunkRegistrationStatusPort trunkRegistrationStatusPort;
    private final Clock clock;
    private final Map<Long, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final Map<Long, Instant> restingUntil = new ConcurrentHashMap<>();

    public TrunkHealth(TrunkRegistrationStatusPort trunkRegistrationStatusPort, Clock clock) {
        this.trunkRegistrationStatusPort = trunkRegistrationStatusPort;
        this.clock = clock;
    }

    /** A call over {@code trunkId} ended this way; null trunk (no trunk chosen) is ignored. */
    @Override
    public void recordOutcome(Long trunkId, Disposition disposition) {
        if (trunkId == null || disposition == null) {
            return;
        }
        if (disposition == Disposition.CARRIER_REJECTED || disposition == Disposition.FAILED) {
            int failures = consecutiveFailures.computeIfAbsent(trunkId, id -> new AtomicInteger()).incrementAndGet();
            if (failures >= FAILURES_BEFORE_REST && !isResting(trunkId)) {
                Instant until = Instant.now(clock).plus(REST);
                restingUntil.put(trunkId, until);
                log.error("SIP trunk {} failed {} calls in a row — resting it until {}", trunkId, failures, until);
            }
            return;
        }
        // Anything the carrier connected — answered, busy, no answer — means the trunk works.
        consecutiveFailures.remove(trunkId);
        restingUntil.remove(trunkId);
    }

    /** Whether the dialer should hand this trunk a call right now. */
    public boolean isUsable(SipTrunkRow trunk) {
        if (trunk == null) {
            return false;
        }
        if (isResting(trunk.id())) {
            return false;
        }
        return !trunkRegistrationStatusPort.isKnownDown(trunk.pjsipEndpoint());
    }

    private boolean isResting(long trunkId) {
        Instant until = restingUntil.get(trunkId);
        if (until == null) {
            return false;
        }
        if (until.isBefore(Instant.now(clock))) {
            restingUntil.remove(trunkId);
            consecutiveFailures.remove(trunkId);
            return false;
        }
        return true;
    }
}
