package uz.murodjon.robotcallv2.agent.rtp;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Hands out even UDP ports from the configured range for externalMedia
 * listeners, one per active call (RTP conventionally uses even ports).
 *
 * <p>First in, first out: a released port goes to the back of the queue, so it is the
 * last one handed out again. Always taking the lowest free port gave a just-freed port
 * straight to the next call, while the previous call's externalMedia leg could still be
 * sending to it.
 */
@Component
public class RtpPortAllocator {

    private final int rangeStart;
    private final int rangeEnd;
    private final Deque<Integer> free = new ArrayDeque<>();

    public RtpPortAllocator(RtpProperties rtpProperties) {
        this.rangeStart = rtpProperties.portRangeStart();
        this.rangeEnd = rtpProperties.portRangeEnd();
        for (int port = rangeStart; port <= rangeEnd; port += 2) {
            free.addLast(port);
        }
    }

    /** @throws ExternalServiceException if no port is available */
    public synchronized int allocate() {
        Integer port = free.pollFirst();
        if (port == null) {
            throw new ExternalServiceException(ErrorCode.RTP_PORTS_EXHAUSTED, "rtp", rangeStart, rangeEnd);
        }
        return port;
    }

    public synchronized void release(int port) {
        if (port >= rangeStart && port <= rangeEnd && !free.contains(port)) {
            free.addLast(port);
        }
    }
}
