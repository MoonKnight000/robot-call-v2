package uz.murodjon.robotcallv2.agent.dialog;

import uz.murodjon.robotcallv2.agent.rtp.RtpEndpoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * What this turn actually put on the wire, as opposed to what the model wrote.
 *
 * <p>The two are the same turn after turn until a barge-in, and then they are not: the
 * caller heard the first sentence and the remaining three were never synthesized. The
 * history, the transcript and the "you were cut off here" note all have to describe
 * what the caller <em>heard</em> — told that it said all four, the model treats the
 * unheard three as delivered and never comes back to them.
 *
 * <p>Thread-safety: the spoken text and audio are guarded by this object (lines are
 * appended from the turn thread and the sentence workers); the unspoken tail is atomic.
 */
class SpokenAudioLedger {

    /** Where playback stands; null on a call with no audio (a simulation), where every line counts whole. */
    private final RtpEndpoint endpoint;

    /** Lines queued without audio accounting — they count whole. */
    private final StringBuilder spokenText = new StringBuilder();

    /**
     * This turn's lines that went out as audio, with where each one sits in the playback
     * stream. What the caller heard of them depends on how far the wire got before a
     * barge-in flushed the queue, so it is worked out when it is asked for, not when the
     * audio was handed over.
     */
    private final List<SpokenAudio> spokenAudio = new ArrayList<>();

    /**
     * The tail of a reply a barge-in stopped before it could be spoken, kept in case the
     * interruption turns out to have been noise. Cleared as soon as it is resumed or a
     * real turn makes it stale.
     */
    private final AtomicReference<String> unspokenText = new AtomicReference<>();

    SpokenAudioLedger(RtpEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    /** Drop the previous turn's spoken text before a new turn starts producing its own. */
    synchronized void clearSpokenText() {
        spokenText.setLength(0);
        spokenAudio.clear();
    }

    /** Note that {@code text} was queued for the caller and is therefore heard. */
    synchronized void appendSpokenText(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (!spokenText.isEmpty()) {
            spokenText.append(' ');
        }
        spokenText.append(text.trim());
    }

    /**
     * Note that {@code text} was queued as audio occupying {@code samples} samples starting
     * at {@code startSample} of this call's playback stream.
     *
     * <p>Queued is not heard. A sentence sits in the endpoint's queue for as long as it
     * takes to say, and a barge-in three words in throws away the rest — so what the caller
     * heard of it is decided later, by how far the wire actually got ({@link #spokenText()}).
     * Recorded even when the turn is already cancelled: the words that did go out were
     * still heard, and the model has to know that they were.
     */
    synchronized void appendSpokenAudio(String text, long startSample, int samples) {
        if (text == null || text.isBlank() || samples <= 0) {
            appendSpokenText(text);
            return;
        }
        spokenAudio.add(new SpokenAudio(text.trim(), startSample, samples));
    }

    /**
     * What the caller has heard of this turn, or {@code null} if nothing.
     *
     * <p>Lines queued without audio accounting count whole. Lines that were queued as audio
     * are measured against the endpoint's playback position: one fully played is whole, one
     * cut partway through is truncated at the last word the caller can have heard, and
     * anything behind that in the queue never reached them at all.
     */
    synchronized String spokenText() {
        StringBuilder heard = new StringBuilder(spokenText);
        long played = endpoint != null ? endpoint.playedSamples() : Long.MAX_VALUE;
        for (SpokenAudio line : spokenAudio) {
            String part = line.heardPart(played);
            if (part == null) {
                break; // this line never started; nothing behind it did either
            }
            if (!heard.isEmpty()) {
                heard.append(' ');
            }
            heard.append(part);
            if (part.length() < line.text().length()) {
                break; // cut off inside this line
            }
        }
        return heard.isEmpty() ? null : heard.toString();
    }

    /** Hold the tail of a reply a barge-in cut off, in case the interruption was noise. */
    void setUnspokenText(String text) {
        unspokenText.set(text == null || text.isBlank() ? null : text.trim());
    }

    /** Take the interrupted reply's unspoken tail, or {@code null} if there is none. */
    String takeUnspokenText() {
        return unspokenText.getAndSet(null);
    }

    /**
     * Non-consuming peek at whether a cut-off reply is still waiting to be resumed — i.e.
     * whether there is anything to go back to if the interruption turns out not to have
     * been one.
     */
    boolean hasUnspokenText() {
        return unspokenText.get() != null;
    }

    /**
     * One line of this turn as it sits in the playback stream: the words, and the samples
     * they occupy.
     *
     * @param startSample where the line begins in the endpoint's playback stream
     * @param samples     how long it is — i.e. where it ends
     */
    private record SpokenAudio(String text, long startSample, int samples) {

        /**
         * The part of this line the caller can have heard by the time the endpoint had
         * sent {@code played} samples, or {@code null} if none of it was.
         */
        String heardPart(long played) {
            if (played <= startSample) {
                return null;
            }
            if (played - startSample >= samples) {
                return text;
            }
            // Synthesis paces a sentence roughly evenly, so the share of its audio that
            // went out is the share of its words that were heard — give or take a word,
            // which is why the cut lands on a word boundary and never mid-word.
            int chars = (int) ((played - startSample) * text.length() / samples);
            int cut = text.lastIndexOf(' ', Math.min(chars, text.length() - 1));
            return cut <= 0 ? null : text.substring(0, cut);
        }
    }
}
