package uz.murodjon.robotcallv2.agent.audio;

import uz.murodjon.robotcallv2.agent.turn.AgentQuestion;
import uz.murodjon.robotcallv2.agent.turn.AutoTurnPolicy;
import uz.murodjon.robotcallv2.agent.turn.TranscriptTurnCues;
import uz.murodjon.robotcallv2.agent.turn.TurnCall;
import uz.murodjon.robotcallv2.agent.turn.TurnCue;
import uz.murodjon.robotcallv2.agent.turn.TurnPolicy;
import uz.murodjon.robotcallv2.agent.turn.TurnSource;

import java.util.Locale;
import java.util.function.BiConsumer;
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
 * <p>Smart endpointing: the hangover is not one number. It shrinks for a short answer,
 * learns the caller's rhythm ({@code DynamicEndpointingProperties}), is extended by the
 * acoustic turn detector, and moves both ways on what the words say — the recognizer's
 * last interim is read for whether the sentence has reached its verb
 * ({@link TranscriptTurnCues}): finished-sounding closes at {@link #setTranscriptCues
 * completeSilenceMs}, unfinished-sounding waits out the extension as well.
 */
public class SpeechGate {

    /** A COMPLETE hypothesis of at most this many words is a quick answer ("ha", "yo'q, ertaga"). */
    private static final int QUICK_ANSWER_WORDS = 2;

    /** An answer to a closed question of at most this many words is over ("ha, vaqtim bor"). */
    private static final int CLOSED_ANSWER_WORDS = 4;

    /** Everything but letters and digits — what {@link #sameWords} ignores. */
    private static final Pattern WORDS_ONLY = Pattern.compile("[^\\p{L}\\p{N}]+");

    private record LlmVerdict(String transcript, TurnCue cue) {
    }

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
     * The long-utterance wait the agent or its current stage asked for, or {@code -1} to
     * leave the configured and learned hangover in charge. Written by the dialog when the
     * stage changes and read on the audio thread, hence volatile.
     */
    private volatile int responseWaitSamples = -1;

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
    /** The silence the last closed utterance waited out, hangover plus extension. */
    private volatile int lastCloseWaitMs;
    /** See {@link #setNoTranscriptClose}; 0 = off. */
    private int noTranscriptCloseSamples;
    /** Audio the gate has been open for with no interim from the recognizer yet. */
    private int openWithoutTranscriptSamples;
    private volatile boolean lastCloseNoise;
    // What decided the close (lastCloseReason): which wait applied, what was added to it, and
    // what the models said — to see which fixed wait still ends turns instead of a model.
    /** The rule {@link #hangoverForCurrentUtterance} last chose. */
    private String waitRule = "post-roll";
    /** What the extension was decided on; "none" when nothing was added. */
    private String extensionRule = "none";
    /** The turn model's (Smart Turn / text turn) verdict on this pause; null when it was not asked. */
    private Boolean turnModelComplete;
    /** Pauses of this utterance the turn model had already judged when VAD speech started them over. */
    private int pauseRestarts;
    private volatile String lastCloseReason = "";
    /** Set when VAD is unusable — the gate then passes everything, as before gating. */
    private boolean bypassed;

    // Semantic end-of-turn check (SmartTurnDetector).
    private BooleanSupplier turnComplete;
    /** Asked whether the utterance is finished beyond doubt; null leaves the wait alone. */
    private BooleanSupplier confidentlyComplete;
    private int earlyCloseSamples;
    /** Whether this utterance has already been offered an early close. */
    private boolean earlyCloseDecided;
    private int maxExtendSamples;

    /** Whether this utterance has already been scored, and what it earned. */
    private boolean extensionDecided;
    private int extensionSamples;

    // What the last interim said about the utterance (TranscriptTurnCues). At most one of
    // the three is set; a neutral interim clears them all, because the verdict was about
    // the words before it. Written on the recognizer's callback thread and read on the
    // RTP consumer thread, hence volatile — a stale read only costs one VAD window.
    /** Reads as mid-sentence — the extension applies whatever the acoustic model says. */
    private volatile boolean continuationClause;
    /** Reads as a one- or two-word answer — the short hangover applies. */
    private volatile boolean confirmedQuickAnswer;
    /** Reads as a finished sentence — {@link #completeHangoverSamples} applies, if set. */
    private volatile boolean completeCue;
    /** Hangover for a finished-sounding utterance; 0 leaves the words unable to shorten it. */
    private int completeHangoverSamples;
    /** The latest interim as said, for a detector that judges the words themselves. */
    private volatile String lastInterim = "";
    /** Words in {@link #lastInterim}; 0 until the recognizer has said anything. */
    private volatile int interimWords;
    /** What the agent's last line asked — written by the dialog, read on the audio thread. */
    private volatile AgentQuestion agentQuestion = AgentQuestion.NONE;
    /** The agent's last line as said, for the close listener's dataset row. */
    private volatile String agentLine = "";
    // What the speculative reply said about the utterance (setLlmTurnCue), and the interim it
    // was written for. One immutable pair, so the two are never read from different verdicts.
    private volatile LlmVerdict llmVerdict;
    /** Asked about every new interim with the agent's line (LlmTurnDetector); null when not installed. */
    private volatile BiConsumer<String, String> llmTurnJudge;
    /** The wait an INCOMPLETE verdict earns — TEN's force threshold; 0 keeps the turn detector's. */
    private int llmUnfinishedSamples;
    /** The wait a WAIT verdict earns: the caller asked for time. */
    private int llmWaitSamples;

    /** How the verdicts below are combined; see {@link #setTurnPolicy}. */
    private volatile TurnPolicy turnPolicy = AutoTurnPolicy.INSTANCE;

    /** Told how each close turned out; null when nobody is listening. */
    private GateCloseListener closeListener;
    /** The last interim when the gate last shut — what the close being judged was made on. */
    private String closedTranscript = "";
    /** The agent's line when the gate last shut: the next one may be spoken before the close is judged. */
    private String closedAgentLine = "";
    /** What ended the utterance when the gate last shut, for the dataset row; null after a noise close. */
    private TurnCloseDecision closedDecision;

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
     * Let the words move the wait: once {@code completeSilenceMs} of silence have passed,
     * an utterance the policy reads as a finished sentence — the language model's verdict,
     * not the word rules alone ({@link TurnPolicy#allowsShortWait}) — closes there instead of
     * waiting out the full hangover. 0 keeps the words from ever shortening the wait — an
     * unfinished-sounding one is still extended regardless.
     *
     * <p>This is the uz-UZ answer to the turnaround budget: the acoustic model cannot be
     * asked ({@link #setEarlyClose}), but a sentence that has reached its verb is over in
     * this language more reliably than any silence threshold can tell.
     */
    public void setTranscriptCues(int completeSilenceMs) {
        this.completeHangoverSamples = Math.max(0, completeSilenceMs) * sampleRate / 1000;
    }

    /**
     * Close an utterance the recognizer has not transcribed a word of after {@code noTranscriptMs}
     * open: that is the VAD scoring breathing or a noisy room, not a caller. Left open, such
     * audio streamed for 25 s at a time until the utterance cap, and the recognizer that rightly
     * said nothing was dropped as stalled. 0 never closes on this.
     */
    public void setNoTranscriptClose(int noTranscriptMs) {
        this.noTranscriptCloseSamples = Math.max(0, noTranscriptMs) * sampleRate / 1000;
    }
    /** Whether the last close was {@link #setNoTranscriptClose} giving up on noise, not the caller pausing. */
    public boolean lastCloseWasNoise() {
        return lastCloseNoise;
    }

    /**
     * The silence a finished long utterance waits out, as the agent ({@code responseWaitMs})
     * or its current stage set it. It replaces the configured and learned hangover; the
     * short-answer and finished-sentence waits still apply but never exceed it, and the
     * turn detector may still extend it. Null hands the wait back to the configuration and
     * whatever the adaptation has learned meanwhile. Safe to call from any thread.
     */
    public void setResponseWaitMs(Integer responseWaitMs) {
        this.responseWaitSamples = responseWaitMs == null ? -1 : Math.max(0, responseWaitMs) * sampleRate / 1000;
    }

    /**
     * The recognizer's latest hypothesis for the utterance in progress. Only its last word
     * matters, and only the latest hypothesis counts: each one replaces the verdict of the
     * one before, since the caller has said more since.
     */
    public void onInterimTranscript(String transcript) {
        if (transcript == null || transcript.isBlank()) {
            return;
        }
        lastInterim = transcript;
        interimWords = TranscriptTurnCues.wordCount(transcript);
        TurnCue cue = TranscriptTurnCues.judge(transcript);
        continuationClause = cue == TurnCue.INCOMPLETE;
        completeCue = cue == TurnCue.COMPLETE;
        confirmedQuickAnswer = completeCue && interimWords <= QUICK_ANSWER_WORDS;
        BiConsumer<String, String> judge = llmTurnJudge;
        if (judge != null) {
            judge.accept(transcript, agentLine);
        }
    }

    /**
     * Ask a language model about every new interim (TEN Turn Detection); its answers come
     * back through {@link #setLlmTurnCue}. An unfinished verdict holds the pause for at most
     * {@code unfinishedWaitMs} of silence and a "wait" verdict at most {@code waitHoldMs},
     * instead of the turn detector's extension — less when the verdict changes mid-pause.
     * The judge must not block: it runs on the recognizer's callback thread.
     */
    public void setLlmTurnDetector(BiConsumer<String, String> judge, int unfinishedWaitMs, int waitHoldMs) {
        this.llmUnfinishedSamples = Math.max(0, unfinishedWaitMs) * sampleRate / 1000;
        this.llmWaitSamples = Math.max(0, waitHoldMs) * sampleRate / 1000;
        this.llmTurnJudge = judge;
    }

    /**
     * What the agent just asked, which moves the wait the way the caller's own words do: a
     * few words that answer a closed question close on the short wait, and an open question
     * takes the short wait away from a brief start ("Endi…") that is only the caller drawing
     * breath. Safe to call from any thread.
     */
    public void setAgentQuestion(AgentQuestion agentQuestion) {
        this.agentQuestion = agentQuestion == null ? AgentQuestion.NONE : agentQuestion;
    }

    /**
     * What the model writing the speculative reply made of the interim {@code transcript}:
     * {@link TurnCue#COMPLETE} when it started answering, {@link TurnCue#INCOMPLETE} when it
     * chose to wait for the caller ({@code waitForCaller}). It takes the word rules' place
     * (see {@link #readsUnfinished}), and only while the recognizer's hypothesis still says those
     * words — a caller who said more since has made it a verdict on a different sentence.
     * Safe to call from any thread.
     */
    public void setLlmTurnCue(String transcript, TurnCue cue) {
        this.llmVerdict = transcript == null || cue == null || cue == TurnCue.UNKNOWN
                ? null : new LlmVerdict(transcript, cue);
    }

    /** The model's verdict on the interim in hand, or {@link TurnCue#UNKNOWN}. */
    private TurnCue llmCue() {
        LlmVerdict verdict = llmVerdict;
        if (verdict == null || !sameWords(verdict.transcript(), lastInterim)) {
            return TurnCue.UNKNOWN;
        }
        return verdict.cue();
    }

    /** Case and punctuation aside, the same words: a recognizer's revisions often differ only there. */
    private static boolean sameWords(String a, String b) {
        return a != null && b != null && WORDS_ONLY.matcher(a).replaceAll("").toLowerCase(Locale.ROOT)
                .equals(WORDS_ONLY.matcher(b).replaceAll("").toLowerCase(Locale.ROOT));
    }

    /** The agent's latest line itself, handed to the close listener. Safe to call from any thread. */
    public void setAgentLine(String agentLine) {
        this.agentLine = agentLine == null ? "" : agentLine;
    }

    /** The agent's latest line — what the caller is answering; empty before the agent has spoken. */
    public String agentLine() {
        return agentLine;
    }

    /** The recognizer's latest hypothesis for the utterance in progress; empty before the first. */
    public String lastInterimTranscript() {
        return lastInterim;
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
        if (open && noTranscriptCloseSamples > 0 && lastInterim.isEmpty()) {
            openWithoutTranscriptSamples += samples;
            if (openWithoutTranscriptSamples >= noTranscriptCloseSamples) {
                close(0, true);
                return;
            }
        }
        if (speech) {
            if (open && extensionDecided) {
                pauseRestarts++; // the turn model had judged this pause; VAD speech started it over
            }
            silenceSamples = 0;
            // The caller carried on, so any verdict on the pause before it is stale: the
            // pause after their next words is a different question.
            extensionDecided = false;
            extensionSamples = 0;
            earlyCloseDecided = false;
            extensionRule = "none";
            turnModelComplete = null;
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
            if (!earlyClose(wait)) {
                return;
            }
            // Scored finished beyond doubt: the rest of the hangover is silence the caller
            // spends waiting for a verdict that is already in.
            extensionDecided = true;
            extensionSamples = 0;
            extensionRule = "none";
            waitRule = "turn-model-early";
            turnModelComplete = true;
            close(silenceSamples, false);
            return;
        }
        if (!extensionDecided) {
            // The turn model is asked once, at the moment the wait runs out: its score cannot
            // change while the line stays silent.
            extensionDecided = true;
            turnModelComplete = turnComplete == null ? null : turnComplete.getAsBoolean();
        }
        // The pause past the wait is judged again on every silent window: the language
        // model's verdict arrives when it arrives, often after the wait has run out. Decided
        // once, a turn model's "unfinished" made the caller sit out its whole extension after
        // the LLM had already said finished, and an LLM "unfinished" that landed mid-pause was
        // never heard. The fixed figures are only ceilings now — a held pause closes the window
        // no source holds it any longer.
        int held = silenceSamples - wait;
        TurnCue model = turnModelComplete == null ? TurnCue.UNKNOWN
                : turnModelComplete ? TurnCue.COMPLETE : TurnCue.INCOMPLETE;
        TurnCall call = turnPolicy.judgePause(llmCue(), model, wordsCue());
        int cap;
        String rule;
        if (call.cue() == TurnCue.WAIT && call.source() == TurnSource.LLM && llmWaitSamples > 0) {
            cap = llmWaitSamples;
            rule = "llm-wait";
        } else if (call.cue() == TurnCue.INCOMPLETE && call.source() == TurnSource.LLM && llmUnfinishedSamples > 0) {
            cap = llmUnfinishedSamples;
            rule = "llm-unfinished";
        } else if (call.holdsOpen()) {
            cap = maxExtendSamples > 0 ? maxExtendSamples : (400 * sampleRate / 1000);
            rule = call.source().label() + "-unfinished";
        } else {
            // Nothing holds the pause: closed on this window, with what was held so far
            // reported under the rule that held it.
            if ("none".equals(extensionRule)) {
                held = 0;
            }
            extensionSamples = held;
            close(wait + held, false);
            return;
        }
        if (held < cap) {
            extensionSamples = held;
            extensionRule = rule;
            return;
        }
        // Held to its ceiling with the source still saying unfinished: a safety stop, not a verdict.
        extensionSamples = cap;
        extensionRule = rule + "-cap";
        close(wait + cap, false);
    }

    /**
     * End the utterance in progress.
     *
     * @param waitSamples the silence it waited out, reported by {@link #lastCloseWaitMs}
     * @param noise       closed on {@link #setNoTranscriptClose}: not judged as a pause, since
     *                    whether "the caller" carries on says nothing about the hangover
     */
    private void close(int waitSamples, boolean noise) {
        TurnCloseDecision decision = noise ? null : closeDecision(waitSamples);
        lastCloseReason = noise ? "noise: no word transcribed" : decision.describe();
        waitRule = "post-roll";
        extensionRule = "none";
        pauseRestarts = 0;
        turnModelComplete = null;
        open = false;
        lastCloseNoise = noise;
        openWithoutTranscriptSamples = 0;
        lastCloseWaitMs = (int) Math.round(waitSamples * 1000d / sampleRate);
        silenceSamples = 0;
        speechSamples = 0;
        extensionDecided = false;
        extensionSamples = 0;
        earlyCloseDecided = false;
        continuationClause = false;
        confirmedQuickAnswer = false;
        completeCue = false;
        closedTranscript = lastInterim;
        closedAgentLine = agentLine;
        closedDecision = decision; // built before lastInterim is cleared: the LLM cue is matched against it
        llmVerdict = null;
        lastInterim = "";
        interimWords = 0;
        // Start judging this close: whether the caller carries on is what says the
        // wait that produced it was too short.
        sinceCloseSamples = reopenGraceSamples > 0 && !noise ? 0 : -1;
    }

    /**
     * Let something decide, when the wait runs out, whether the caller has actually
     * finished a thought — and give an utterance it calls unfinished up to {@code maxExtendMs}
     * more silence, ended early once the policy no longer holds the pause (see {@code SmartTurnDetector}).
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
     * Let the same detector also <em>shorten</em> the wait: once {@code earlyWaitMs} of
     * silence have passed, an utterance it is confident is finished closes there instead
     * of waiting out the rest of the hangover.
     *
     * <p>This is where the turnaround budget is actually won — the hangover has to be long
     * enough for the caller who pauses to think, and every caller who does not pays it on
     * every turn. Asked once per utterance, and only above the earliest wait: a verdict
     * taken while the caller is drawing breath is a verdict on half a sentence.
     *
     * @param confidentlyComplete must answer {@code false} when it does not know — this
     *                            decides whether a caller is cut off
     * @param earlyWaitMs         silence before the question is worth asking; 0 disables
     */
    public void setEarlyClose(BooleanSupplier confidentlyComplete, int earlyWaitMs) {
        this.confidentlyComplete = confidentlyComplete;
        this.earlyCloseSamples = Math.max(0, earlyWaitMs) * sampleRate / 1000;
    }

    /**
     * Whether the utterance in hand can be closed on this window, before its hangover has
     * run out. Asked at most once per utterance, and never on a short answer — those are
     * already on the aggressive wait and have nothing left to give.
     */
    private boolean earlyClose(int wait) {
        if (confidentlyComplete == null || earlyCloseSamples <= 0 || earlyCloseDecided
                || silenceSamples < earlyCloseSamples || earlyCloseSamples >= wait) {
            return false;
        }
        earlyCloseDecided = true;
        return !readsUnfinished() && confidentlyComplete.getAsBoolean();
    }

    /**
     * The words read as mid-sentence: the language model's verdict on them when it has one,
     * the suffix rules ({@link TranscriptTurnCues}) only when it has none. The rules see one
     * last word; the model sees the sentence and the agent's question — "… odam edimda" ends
     * on "-da", which the rules call unfinished while the model and Smart Turn said finished,
     * and the caller waited 12 s while background voices kept restarting the extension.
     */
    private boolean readsUnfinished() {
        TurnCue words = turnPolicy.readWords(llmCue(), wordsCue());
        return words == TurnCue.INCOMPLETE || words == TurnCue.WAIT;
    }

    /** What the word rules ({@link TranscriptTurnCues}) read in the last interim. */
    private TurnCue wordsCue() {
        return continuationClause ? TurnCue.INCOMPLETE : completeCue ? TurnCue.COMPLETE : TurnCue.UNKNOWN;
    }

    /**
     * How this call's verdicts are combined ({@link TurnPolicy}); {@link AutoTurnPolicy} — every
     * installed source, as the gate always weighed them — until told otherwise.
     */
    public void setTurnPolicy(TurnPolicy turnPolicy) {
        this.turnPolicy = turnPolicy == null ? AutoTurnPolicy.INSTANCE : turnPolicy;
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
        boolean continued = sinceCloseSamples <= reopenGraceSamples;
        if (continued) {
            adaptTowards(hangoverSamples);
        }
        sinceCloseSamples = -1;
        notifyCloseJudged(continued);
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
            notifyCloseJudged(false);
        }
    }

    /** Moves the learned hangover; a gate that does not learn only judges, for the listener. */
    private void adaptTowards(int target) {
        if (dynamic) {
            emaHangoverSamples += emaAlpha * (target - emaHangoverSamples);
        }
    }

    /**
     * Report how a close turned out — what it was made on and whether it held. This is the
     * label a semantic end-of-turn model is trained on: the interim at the close, and
     * whether the caller in fact carried on.
     */
    public void setCloseListener(GateCloseListener closeListener) {
        this.closeListener = closeListener;
    }

    private void notifyCloseJudged(boolean continued) {
        if (closeListener != null && closedDecision != null) {
            closeListener.closeJudged(closedTranscript, lastCloseWaitMs, continued, closedAgentLine, closedDecision);
        }
        closedTranscript = "";
        closedAgentLine = "";
        closedDecision = null;
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
        int requested = responseWaitSamples;
        int shortWait = requested >= 0 ? Math.min(shortHangoverSamples, requested) : shortHangoverSamples;
        if (confirmedQuickAnswer && shortHangoverSamples > 0) {
            waitRule = "quick-answer";
            return shortWait;
        }
        // The short waits below need a positive "finished" from the policy — the language
        // model's, as a rule (TurnPolicy#allowsShortWait). Until it has said so the utterance
        // waits on the full hangover, and this is asked again every silent window, so a
        // verdict that lands mid-pause closes the turn on the window it arrives.
        boolean shortWaitAllowed = turnPolicy.allowsShortWait(llmCue(), wordsCue());
        AgentQuestion question = agentQuestion;
        int words = interimWords;
        // "ha, vaqtim bor" after "vaqtingiz bormi?" is the whole answer however slowly it
        // was said; the recognizer has to have heard it, and it must not lead on.
        if (question == AgentQuestion.CLOSED && shortHangoverSamples > 0 && shortWaitAllowed
                && words > 0 && words <= CLOSED_ANSWER_WORDS) {
            waitRule = "closed-answer";
            return shortWait;
        }
        // A short run that reads as unfinished ("men…") is the start of an answer, not one:
        // the short wait answered half a sentence instead of the caller. After an open
        // question any short run is that start.
        boolean shortAnswer = shortHangoverSamples > 0 && speechSamples <= shortUtteranceSamples
                && shortWaitAllowed && question != AgentQuestion.OPEN;
        if (shortAnswer) {
            waitRule = "short-run";
            return shortWait;
        }
        int wait = requested >= 0 ? requested
                : dynamic ? (int) Math.round(emaHangoverSamples) : hangoverSamples;
        // A finished-sounding sentence does not wait out a budget sized for the caller who
        // might still be mid-thought — the model already said they are not.
        if (shortWaitAllowed && completeHangoverSamples > 0) {
            waitRule = "complete-words";
            return Math.min(completeHangoverSamples, wait);
        }
        waitRule = requested >= 0 ? "response-wait" : dynamic ? "learned-post-roll" : "post-roll";
        return wait;
    }

    /**
     * The silence the utterance that just closed actually had to wait out — its own
     * hangover plus whatever the turn detector added, not the long-utterance figure
     * {@link #hangoverMs()} reports. This is the caller's wait: they had finished
     * speaking for all of it.
     */
    public int lastCloseWaitMs() {
        return lastCloseWaitMs;
    }

    /**
     * What ended the last utterance, for the log: the wait rule and what was added to it,
     * with the models' verdicts — e.g. {@code "post-roll 1200ms + words-unfinished 500ms
     * (turn-model=complete, llm=COMPLETE, words=INCOMPLETE, restarts-after-verdict=0)"}. Empty before the first close.
     */
    public String lastCloseReason() {
        return lastCloseReason;
    }

    /** Read before the close clears the utterance's state. */
    private TurnCloseDecision closeDecision(int waitSamples) {
        int extensionMs = (int) Math.round(extensionSamples * 1000d / sampleRate);
        int baseMs = (int) Math.round(waitSamples * 1000d / sampleRate) - extensionMs;
        String turnModel = turnModelComplete == null ? "not-asked" : turnModelComplete ? "complete" : "unfinished";
        return new TurnCloseDecision(waitRule, baseMs, extensionRule, extensionMs, turnModel, llmCue(), wordsCue(),
                pauseRestarts, turnPolicy.name());
    }

    /**
     * The wait a long utterance currently earns, in milliseconds. Fixed unless the
     * adaptation is on, in which case it is what this call has learned so far — the one
     * number that says whether the learning is landing anywhere sensible.
     */
    public int hangoverMs() {
        int requested = responseWaitSamples;
        if (requested >= 0) {
            return (int) Math.round(requested * 1000d / sampleRate);
        }
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
