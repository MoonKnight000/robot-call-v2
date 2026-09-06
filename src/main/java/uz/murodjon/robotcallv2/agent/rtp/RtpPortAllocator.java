package uz.murodjon.robotcallv2.agent.rtp;

import org.springframework.stereotype.Component;

import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Hands out even UDP ports from the configured range for externalMedia
 * listeners, one per active call (RTP conventionally uses even ports).
 */
@Component
public class RtpPortAllocator {

    private final int rangeStart;
    private final int rangeEnd;
    private final NavigableSet<Integer> free = new TreeSet<>();

    public RtpPortAllocator(RtpProperties rtpProperties) {
        this.rangeStart = rtpProperties.portRangeStart();
        this.rangeEnd = rtpProperties.portRangeEnd();
        for (int port = rangeStart; port <= rangeEnd; port += 2) {
            free.add(port);
        }
    }

    /** @throws IllegalStateException if no port is available */
    public synchronized int allocate() {
        Integer port = free.pollFirst();
        if (port == null) {
            throw new IllegalStateException("No free RTP ports in range " + rangeStart + "-" + rangeEnd);
        }
        return port;
    }

    public synchronized void release(int port) {
        if (port >= rangeStart && port <= rangeEnd) {
            free.add(port);
        }
    }
}
