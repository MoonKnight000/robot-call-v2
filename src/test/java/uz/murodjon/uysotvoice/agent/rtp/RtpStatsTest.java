package uz.murodjon.uysotvoice.agent.rtp;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * These counters are read when a call has already gone wrong, so what matters is that
 * they accuse the right party: real loss must show up, and the things that merely look
 * like loss — a wrapped sequence number, a packet that overtook another, a sender that
 * restarted mid-call — must not.
 */
class RtpStatsTest {

    private static final int CLOCK = 8000;
    /** One 20ms G.711 frame at 8 kHz. */
    private static final int TICKS_PER_FRAME = 160;
    private static final long FRAME_NANOS = TimeUnit.MILLISECONDS.toNanos(20);

    /** Feed {@code count} packets arriving exactly on the 20ms beat from {@code firstSeq}. */
    private static void sendSteady(RtpStats stats, int firstSeq, int count) {
        for (int i = 0; i < count; i++) {
            int seq = (firstSeq + i) & 0xFFFF;
            stats.onPacket(seq, (long) i * TICKS_PER_FRAME, i * FRAME_NANOS);
        }
    }

    @Test
    void countsNothingLostOnACleanStream() {
        RtpStats stats = new RtpStats(CLOCK);

        sendSteady(stats, 100, 50);

        assertThat(stats.receivedPackets()).isEqualTo(50);
        assertThat(stats.lostPackets()).isZero();
        assertThat(stats.lossPercent()).isZero();
    }

    @Test
    void countsTheGapBetweenSequenceNumbersAsLoss() {
        RtpStats stats = new RtpStats(CLOCK);

        stats.onPacket(1, 0, 0);
        stats.onPacket(5, 4L * TICKS_PER_FRAME, 4 * FRAME_NANOS); // 2, 3, 4 never arrived

        assertThat(stats.receivedPackets()).isEqualTo(2);
        assertThat(stats.lostPackets()).isEqualTo(3);
        assertThat(stats.lossPercent()).isEqualTo(60.0); // 3 of the 5 the sender emitted
    }

    @Test
    void aLatePacketFillsTheGapItWasCountedFor() {
        RtpStats stats = new RtpStats(CLOCK);

        stats.onPacket(1, 0, 0);
        stats.onPacket(3, 2L * TICKS_PER_FRAME, 2 * FRAME_NANOS); // 2 looks lost
        stats.onPacket(2, TICKS_PER_FRAME, 3 * FRAME_NANOS);      // ... but was only late

        assertThat(stats.reorderedPackets()).isEqualTo(1);
        assertThat(stats.lostPackets()).isZero();
    }

    @Test
    void sequenceWrapAroundIsNotLoss() {
        RtpStats stats = new RtpStats(CLOCK);

        sendSteady(stats, 0xFFFE, 4); // 65534, 65535, 0, 1

        assertThat(stats.receivedPackets()).isEqualTo(4);
        assertThat(stats.lostPackets()).isZero();
    }

    @Test
    void aSenderThatRestartsResyncsInsteadOfBookingThousandsOfLosses() {
        RtpStats stats = new RtpStats(CLOCK);

        sendSteady(stats, 100, 5);
        // A re-INVITE brings a new SSRC and a fresh sequence base far outside the window.
        stats.onPacket(40_000, 0, 10 * FRAME_NANOS);
        stats.onPacket(40_001, TICKS_PER_FRAME, 11 * FRAME_NANOS);

        assertThat(stats.lostPackets()).isZero();
        assertThat(stats.receivedPackets()).isEqualTo(7);
    }

    @Test
    void jitterStaysAtZeroWhenPacketsArriveOnTheBeat() {
        RtpStats stats = new RtpStats(CLOCK);

        sendSteady(stats, 1, 30);

        assertThat(stats.jitterMillis()).isZero();
    }

    @Test
    void latePacketsRaiseJitter() {
        RtpStats stats = new RtpStats(CLOCK);

        // Same media timestamps, but arrival drifts 10ms further behind every packet.
        for (int i = 0; i < 30; i++) {
            long arrival = i * FRAME_NANOS + (long) i * TimeUnit.MILLISECONDS.toNanos(10);
            stats.onPacket(i + 1, (long) i * TICKS_PER_FRAME, arrival);
        }

        assertThat(stats.jitterMillis()).isGreaterThan(5.0);
    }

    @Test
    void aCallWithNoPacketsHasNoLossToReport() {
        RtpStats stats = new RtpStats(CLOCK);

        assertThat(stats.receivedPackets()).isZero();
        assertThat(stats.lossPercent()).isZero();
        assertThat(stats.jitterMillis()).isZero();
    }
}
