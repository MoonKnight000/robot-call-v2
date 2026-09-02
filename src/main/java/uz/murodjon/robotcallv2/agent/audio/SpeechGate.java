package uz.murodjon.robotcallv2.agent.audio;

import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Decides which of a call's audio is worth sending to the speech recognizer.
 *
 * <p>Yandex bills STT per streamed audio-second, and a debt-collection call is mostly
 * not speech: the bot talks, the caller thinks, the line sits idle. Streaming all of it
 * paid full price for silence. The VAD that already runs for barge-in scores every
 * window anyway, so the same signal opens and closes a gate in front of the STT stream.
 *
 * <p>The moment it shuts is also this call's end-of-utterance signal when the recognizer
 * has been told to stop deciding that for itself ({@code EndpointingProperties}), which
 * is why the hangover can differ per utterance: see
 * {@link #hangoverForCurrentUtterance()}.
 *
 * <p>Two margins keep the recognizer whole. A <b>pre-roll</b> ring buffer holds the last
 * few hundred milliseconds while the gate is shut, so the syllables that arrive before
 * the VAD has made up its mind are still delivered — without it every utterance loses
 * its first word. A <b>post-roll</b> hangover keeps the stream open through the pause
 * after speech, because the provider's end-of-utterance detector needs to hear that
 * silence to emit a final; cutting the audio the moment speech stops would leave the
 * turn hanging with no final at all.
 *
 * <p>Smart Endpointing: adjusts silence threshold dynamically based on speech length,
 * acoustic turn detector, and interim linguistic hints (continuation clauses vs quick answers).
 */
public class SpeechGate {

    private static final Set<String> CONTINUATION_WORDS = Set.of(
            // Uzbek
            "chunki", "lekin", "ammo", "agar", "va", "balki", "yoki", "hamda", "modomiki",
            // Russian
            "потому", "потому что", "но", "если", "и", "а", "или", "хотя", "ведь",
            // English
            "because", "but", "if", "and", "or", "although", "while", "so"
    );

    private static final Set<String> QUICK_ANSWERS = Set.of(
            // Uzbek
            "ha", "yo'q", "yoq", "eshitaman", "albatta", "tushundim", "mayli", "rahmat",
            // Russian
            "да", "нет", "слушаю", "конечно", "понятно", "хорошо", "спасибо",
            // English
            "yes", "no", "yeah", "nope", "sure", "ok", "okay", "thanks"
    );

    private static final Pattern WORD_SPLIT = Pattern.compile("[\\s,;:.!?]+");

    private final int sampleRate;
    private final int hangoverSamples;
    private final int shortHangoverSamples;
    private final int shortUtteranceSamples;
    private final int openSamples;
    private final short[] preRoll;

    // Adaptive long-utterance hangover (DynamicEndpointingProperties).
    private final boolean dynamic;
    private final int minHangoverSamples;
    private final int reopenGraceSamples;
    private final double emaAlpha;

    /** The learned long-utterance hangover; starts at the configured maximum. */
    private double emaHangoverSamples;

    /**
     * Audio seen since the gate last shut, or {@code -1} when nothing is being judged.
     */
    private int sinceCloseSamples = -1;

    /** Write position in the ring, and how many samples it currently holds. */
    private int writePos;
    private int filled;

    private boolean open;
    private int silenceSamples;
    /** Speech in the utterance being gated — what decides which hangover applies. */
    private int speechSamples;

    /** Set when VAD is unusable — the gate then passes everything, as before gating. */
    private boolean bypassed;

    // Semantic end-of-turn check (SmartTurnDetector).
    private BooleanSupplier turnComplete;
    private int maxExtendSamples;

    /** Whether this utterance has already been scored, and what it earned. */
    private boolean extensionDecided;
    private int extensionSamples;

    /** Linguistic smart endpointing flags updated via interim transcripts. */
    private boolean continuationClause;
    private boolean confirmedQuickAnswer;

    public SpeechGate(int sampleRate, int preRollMs, int postRollMs, int minSpeechMs,
                      int shortUtteranceMs, int shortPostRollMs,
                      int minPostRollMs, double emaAlpha, int reopenGraceMs) {
        this.sampleRate = sampleRate;
        this.hangoverSamples = Math.max(0, postRollMs) * sampleRate / 1000;
        this.shortHangoverSamples = Math.max(0, shortPostRollMs) * sampleRate / 1000;
        this.shortUtteranceSamples = Math.max(0, shortUtteranceMs) * sampleRate / 1000;
        this.openSamples = Math.max(0, minSpeechMs) * sampleRate / 1000;
        this.preRoll = new short[Math.max(1, Math.max(0, preRollMs) * sampleRate / 1000)];
        this.minHangoverSamples = Math.min(Math.max(0, minPostRollMs) * sampleRate / 1000, hangoverSamples);
        this.reopenGraceSamples = Math.max(0, reopenGraceMs) * sampleRate / 1000;
        this.emaAlpha = Math.min(Math.max(emaAlpha, 0d), 1d);
        this.dynamic = reopenGraceSamples > 0 && this.emaAlpha > 0 && minHangoverSamples < hangoverSamples;
        this.emaHangoverSamples = hangoverSamples;
    }

    /**
     * Feeds interim recognition transcript to adjust the silence endpointing threshold.
     */
    public void onInterimTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return;
        }
        String cleaned = transcript.trim().toLowerCase();
        String[] words = WORD_SPLIT.split(cleaned);
        if (words.length == 0) {
            return;
        }
        String lastWord = words[words.length - 1];
        if (CONTINUATION_WORDS.contains(lastWord) || CONTINUATION_WORDS.contains(cleaned)) {
            continuationClause = true;
            confirmedQuickAnswer = false;
        } else if (words.length <= 2 && QUICK_ANSWERS.contains(cleaned)) {
            confirmedQuickAnswer = true;
            continuationClause = false;
        }
    }

    /**
     * One scored VAD window. Speech opens the gate once it has run for {@code minSpeechMs}
     * (the pre-roll covers what came before, so nothing is lost by confirming first);
     * silence closes it only after the hangover this utterance has earned.
     *
     * @param speech  whether the window scored above the VAD threshold
     * @param samples how many samples the window covered
     */
    public void onVadWindow(boolean speech, int samples) {
        if (bypassed) {
            return;
        }
        if (sinceCloseSamples >= 0) {
            sinceCloseSamples += samples;
        }
        if (speech) {
            silenceSamples = 0;
            // The caller carried on, so any verdict on the pause before it is stale: the
            // pause after their next words is a different question.
            extensionDecided = false;
            extensionSamples = 0;
            speechSamples += samples;
            if (!open && speechSamples >= openSamples) {
                open = true;
                judgeReopen();
            }
            return;
        }
        if (!open) {
            // A run of speech that never reached openSamples was not an utterance —
            // someone talking across the room, a cough, a door. Start the count over
            // rather than letting unrelated blips add up to an open gate.
            speechSamples = 0;
            judgeSilenceAfterClose();
            return;
        }
        silenceSamples += samples;
        int wait = hangoverForCurrentUtterance();
        if (silenceSamples < wait) {
            return;
        }
        if (!extensionDecided) {
            // Asked once, at the moment the timer would have closed the utterance: the
            // model costs a few milliseconds and the answer cannot change while the line
            // stays silent, so scoring every window afterwards would buy nothing.
            extensionDecided = true;
            boolean complete = turnComplete == null || turnComplete.getAsBoolean();
            if (!complete || continuationClause) {
                extensionSamples = maxExtendSamples > 0 ? maxExtendSamples : (400 * sampleRate / 1000);
            } else {
                extensionSamples = 0;
            }
        }
        if (silenceSamples < wait + extensionSamples) {
            return;
        }
        open = false;
        silenceSamples = 0;
        speechSamples = 0;
        extensionDecided = false;
        extensionSamples = 0;
        continuationClause = false;
        confirmedQuickAnswer = false;
        // Start judging this close: whether the caller carries on is what says the
        // wait that produced it was too short.
        sinceCloseSamples = dynamic ? 0 : -1;
    }

    /**
     * Let something decide, when the wait runs out, whether the caller has actually
     * finished a thought — and give an utterance it calls unfinished {@code maxExtendMs}
     * more silence (see {@code SmartTurnDetector}).
     *
     * <p>Only ever extends. The timer's wait is the floor whatever the model says, because
     * a model asked to shorten it is a model deciding when a caller gets interrupted, and
     * this one has no Uzbek in it.
     */
    public void setTurnDetector(BooleanSupplier turnComplete, int maxExtendMs) {
        this.turnComplete = turnComplete;
        this.maxExtendSamples = Math.max(0, maxExtendMs) * sampleRate / 1000;
    }

    /**
     * The gate reopened. Within the grace window that means the close cut a pause the
     * caller was going to speak through — a mid-sentence breath, not the end of a turn —
     * so the wait grows back towards the configured maximum.
     *
     * <p>Judged on the reopen rather than on the first speech window after a close: a
     * cough or a door is exactly what {@code minSpeechMs} exists to discount, and letting
     * one push the wait up would leave a noisy line permanently at its slowest.
     */
    private void judgeReopen() {
        if (sinceCloseSamples < 0) {
            return;
        }
        if (sinceCloseSamples <= reopenGraceSamples) {
            adaptTowards(hangoverSamples);
        }
        sinceCloseSamples = -1;
    }

    /**
     * The grace window passed with nobody speaking, so the close really was the end of a
     * turn — and the wait that produced it was, if anything, longer than this caller
     * needs. Shrink it by one utterance's worth, towards the floor.
     */
    private void judgeSilenceAfterClose() {
        if (sinceCloseSamples > reopenGraceSamples) {
            adaptTowards(minHangoverSamples);
            sinceCloseSamples = -1;
        }
    }

    private void adaptTowards(int target) {
        emaHangoverSamples += emaAlpha * (target - emaHangoverSamples);
    }

    /**
     * How much silence closes the utterance in hand. A word or two ("ha", "yo'q",
     * "eshitaman") is over the moment it stops, and making it wait as long as a dictated
     * contract number costs that wait on the most common turn there is. Anything longer
     * keeps the conservative hangover: it is the utterance that can still be
     * mid-sentence, and cutting that one short is what produces half-heard turns.
     *
     * <p>That conservative hangover is the one the adaptation moves — a short answer is
     * already on the aggressive path and has nothing left to learn.
     */
    private int hangoverForCurrentUtterance() {
        if (confirmedQuickAnswer && shortHangoverSamples > 0) {
            return shortHangoverSamples;
        }
        boolean shortAnswer = shortHangoverSamples > 0 && speechSamples <= shortUtteranceSamples;
        if (shortAnswer) {
            return shortHangoverSamples;
        }
        return dynamic ? (int) Math.round(emaHangoverSamples) : hangoverSamples;
    }

    /**
     * The wait a long utterance currently earns, in milliseconds. Fixed unless the
     * adaptation is on, in which case it is what this call has learned so far — the one
     * number that says whether the learning is landing anywhere sensible.
     */
    public int hangoverMs() {
        return (int) Math.round((dynamic ? emaHangoverSamples : hangoverSamples) * 1000d / sampleRate);
    }

    /**
     * Stop gating for the rest of the call — used when VAD inference fails. Recognition
     * quality is never traded for the saving: no VAD means no gate.
     */
    public void bypass() {
        bypassed = true;
        open = true;
    }

    public boolean isOpen() {
        return open;
    }

    /** Hold audio that arrived while the gate was shut, oldest samples dropped first. */
    public void buffer(short[] pcm, int length) {
        int count = Math.min(length, preRoll.length);
        // Only the tail can survive in the ring, so skip straight to it.
        int start = length - count;
        for (int i = 0; i < count; i++) {
            preRoll[writePos] = pcm[start + i];
            writePos = (writePos + 1) % preRoll.length;
        }
        filled = Math.min(filled + count, preRoll.length);
    }

    /**
     * Take everything buffered while the gate was shut, oldest first, and empty the
     * ring. Returns {@code null} when there is nothing held.
     */
    public short[] drainPreRoll() {
        if (filled == 0) {
            return null;
        }
        short[] out = new short[filled];
        int start = (writePos - filled + preRoll.length) % preRoll.length;
        for (int i = 0; i < filled; i++) {
            out[i] = preRoll[(start + i) % preRoll.length];
        }
        filled = 0;
        writePos = 0;
        return out;
    }
}
