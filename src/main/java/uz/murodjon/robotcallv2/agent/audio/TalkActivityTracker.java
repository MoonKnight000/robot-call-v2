package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.callrecord.domain.entity.CallTalkMetrics;

import java.util.BitSet;

/**
 * Marks, in 20 ms steps of wall-clock time, when each of the two people on a call was
 * speaking, and sums it up into {@link CallTalkMetrics} once the call is over.
 *
 * <p>Each side is fed its own audio, as it arrives, so the two line up by arrival time:
 * they come from separate streams and no shared sample clock exists. A step is speech when
 * its level clears {@link #SPEECH_RMS}; pauses up to {@link #PAUSE_FILL_STEPS} inside speech
 * count as speech too, or every gap between words would be "silence".
 */
public class TalkActivityTracker {

    static final int STEP_MS = 20;
    /** Telephone speech sits well above this; line noise well below it. */
    public static final double SPEECH_RMS = 500;
    /** 300 ms: the gap between words and short breaths, not a pause anyone would notice. */
    static final int PAUSE_FILL_STEPS = 15;

    private static final long STEP_NANOS = STEP_MS * 1_000_000L;

    private final long startNanos;
    private final BitSet client = new BitSet();
    private final BitSet operator = new BitSet();
    private int lastStep = -1;

    public TalkActivityTracker(long startNanos) {
        this.startNanos = startNanos;
    }

    public void onClientAudio(short[] pcm, int length, long nowNanos) {
        mark(client, pcm, length, nowNanos);
    }

    public void onOperatorAudio(short[] pcm, int length, long nowNanos) {
        mark(operator, pcm, length, nowNanos);
    }

    /** The totals for {@code callId}; null when no audio arrived at all. */
    public synchronized CallTalkMetrics summarize(long callId) {
        if (lastStep < 0) {
            return null;
        }
        BitSet clientSpeech = fillPauses(client);
        BitSet operatorSpeech = fillPauses(operator);
        int clientSteps = 0;
        int operatorSteps = 0;
        int bothSteps = 0;
        int silentSteps = 0;
        int silentRun = 0;
        int longestSilentRun = 0;
        for (int step = 0; step <= lastStep; step++) {
            boolean clientTalks = clientSpeech.get(step);
            boolean operatorTalks = operatorSpeech.get(step);
            clientSteps += clientTalks ? 1 : 0;
            operatorSteps += operatorTalks ? 1 : 0;
            bothSteps += clientTalks && operatorTalks ? 1 : 0;
            if (clientTalks || operatorTalks) {
                silentRun = 0;
            } else {
                silentSteps++;
                silentRun++;
                longestSilentRun = Math.max(longestSilentRun, silentRun);
            }
        }
        return new CallTalkMetrics(callId, (lastStep + 1) * STEP_MS, clientSteps * STEP_MS, operatorSteps * STEP_MS,
                bothSteps * STEP_MS, silentSteps * STEP_MS, longestSilentRun * STEP_MS);
    }

    private synchronized void mark(BitSet side, short[] pcm, int length, long nowNanos) {
        int step = (int) Math.max(0, (nowNanos - startNanos) / STEP_NANOS);
        lastStep = Math.max(lastStep, step);
        if (rms(pcm, length) >= SPEECH_RMS) {
            side.set(step);
        }
    }

    /** {@code side} with every gap of up to {@link #PAUSE_FILL_STEPS} between two speech steps filled in. */
    private static BitSet fillPauses(BitSet side) {
        BitSet filled = (BitSet) side.clone();
        int end = side.nextSetBit(0);
        while (end >= 0) {
            int gapStart = side.nextClearBit(end);
            int next = side.nextSetBit(gapStart);
            if (next < 0) {
                break;
            }
            if (next - gapStart <= PAUSE_FILL_STEPS) {
                filled.set(gapStart, next);
            }
            end = next;
        }
        return filled;
    }

    public static double rms(short[] pcm, int length) {
        if (length <= 0) {
            return 0;
        }
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (double) pcm[i] * pcm[i];
        }
        return Math.sqrt(sum / length);
    }
}
