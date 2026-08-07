package uz.murodjon.uysotvoice.agent.rtp;

/**
 * Receive-side quality of one call's inbound RTP stream (RFC 3550 §6.4.1): how many
 * packets arrived, how many the sequence numbers say never did, how many turned up
 * late or twice, and the interarrival jitter.
 *
 * <p>Without this a call the caller was never heard on is indistinguishable from one
 * where recognition failed — both end as a silent caller with an empty transcript.
 * Loss and jitter say the network chewed the audio; zero packets says the media path
 * was never established at all (the classic {@code RTP_LOCAL_IP}/NAT misconfiguration,
 * see docs/NETWORK.md). Neither shows up in the STT, dialog or disposition metrics.
 *
 * <p>Every method is synchronized: packets are counted on the call's RTP consumer
 * thread while the read-out happens on the teardown thread.
 */
public class RtpStats {

    /**
     * A forward jump larger than this is not loss but a new sender — a re-INVITE
     * restarts the sequence from a fresh base, and booking that as ~30000 lost packets
     * would make every such call look catastrophic. RFC 3550's own constants.
     */
    private static final int MAX_DROPOUT = 3000;
    private static final int MAX_MISORDER = 100;

    private static final int SEQ_MODULUS = 0x10000;

    /** RFC 3550's jitter smoothing factor: J += (|D| - J)/16. */
    private static final double JITTER_GAIN = 16.0;

    private final long nanosPerTick;

    private long received;
    private long lost;
    private long reordered;

    private boolean started;
    private int expectedSequence;

    private boolean transitKnown;
    private long lastTransit;
    /** Smoothed jitter, in RTP timestamp ticks. */
    private double jitter;

    /** @param clockRate the stream's RTP clock in Hz (8000 for G.711 telephony audio) */
    public RtpStats(int clockRate) {
        this.nanosPerTick = 1_000_000_000L / clockRate;
    }

    /**
     * Book one arrived packet.
     *
     * @param sequenceNumber the packet's 16-bit sequence number
     * @param rtpTimestamp   its media timestamp, in the stream's clock ticks
     * @param arrivalNanos   {@code System.nanoTime()} taken when the datagram was read,
     *                       not when it was processed — queueing delay of our own would
     *                       otherwise be counted as the network's jitter
     */
    public synchronized void onPacket(int sequenceNumber, long rtpTimestamp, long arrivalNanos) {
        received++;
        updateJitter(rtpTimestamp, arrivalNanos);

        if (!started) {
            started = true;
            expectedSequence = next(sequenceNumber);
            return;
        }
        int delta = (sequenceNumber - expectedSequence) & 0xFFFF;
        if (delta == 0) {
            expectedSequence = next(sequenceNumber);
        } else if (delta < MAX_DROPOUT) {
            lost += delta; // the packets between the last one and this one never came
            expectedSequence = next(sequenceNumber);
        } else if (delta > SEQ_MODULUS - MAX_MISORDER) {
            // Arrived out of order, or twice. It fills a gap already counted as lost.
            reordered++;
            if (lost > 0) {
                lost--;
            }
        } else {
            expectedSequence = next(sequenceNumber); // new sender — resync, count nothing
        }
    }

    public synchronized long receivedPackets() {
        return received;
    }

    public synchronized long lostPackets() {
        return lost;
    }

    public synchronized long reorderedPackets() {
        return reordered;
    }

    /** Smoothed interarrival jitter in milliseconds. */
    public synchronized double jitterMillis() {
        return jitter * nanosPerTick / 1_000_000.0;
    }

    /** Share of the packets the sender emitted that never arrived, in percent. */
    public synchronized double lossPercent() {
        long sent = received + lost;
        return sent == 0 ? 0.0 : lost * 100.0 / sent;
    }

    private void updateJitter(long rtpTimestamp, long arrivalNanos) {
        // Both sides in clock ticks: the constant offset between our clock and the
        // sender's cancels out in the difference below.
        long transit = arrivalNanos / nanosPerTick - rtpTimestamp;
        if (transitKnown) {
            long d = Math.abs(transit - lastTransit);
            jitter += (d - jitter) / JITTER_GAIN;
        }
        lastTransit = transit;
        transitKnown = true;
    }

    private static int next(int sequenceNumber) {
        return (sequenceNumber + 1) & 0xFFFF;
    }
}
