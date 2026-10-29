package uz.murodjon.robotcallv2.agent.metrics;

import java.time.Instant;

/**
 * Where one turn's wait actually went, stage by stage.
 *
 * <p>{@code voice.turnaround.latency} says how long the caller waited; it does not say
 * what for. A turn that takes 1.6 s is a recognizer problem, a model problem or a
 * synthesis problem, and the three are fixed in different places — so tuning without this
 * breakdown is guessing. Worse, the §1.3 turnaround clock starts at the <em>final
 * transcript</em>, which means the endpointing silence in front of it — the largest single
 * block of the wait on most turns — is not in that number at all.
 *
 * <p>Five stamps, taken where each moment is actually known:
 *
 * <ul>
 *   <li><b>eou</b> — silence the gate waited out before declaring the utterance over
 *       ({@code SpeechGate}); the caller was already finished for all of it
 *   <li><b>stt_final</b> — end of utterance → the recognizer's final text
 *   <li><b>llm_ttft</b> — request sent → first token back
 *   <li><b>tts_ttfb</b> — first sentence handed to synthesis → first PCM chunk
 *   <li><b>first_audio</b> — end of utterance → audio on the wire, i.e. the whole silence
 *       from the caller's side
 * </ul>
 *
 * <p>The first two and the last need a gate to close, and with the recognizer doing its
 * own endpointing there is none. That configuration reports <b>reply_wait</b> instead —
 * final → audio, the part of the silence this app is responsible for — and leaves the
 * endpointing silence in front of it unmeasured rather than quietly missing from a total.
 *
 * <p>Written from several threads (the RTP thread closes the gate, the recognizer's thread
 * delivers the final, the turn's thread runs the LLM), so every stamp is volatile. Nothing
 * here is load-bearing: a missing stamp reports {@code -1} for its stage rather than a
 * number that was never measured.
 */
public class TurnLatency {

    /** Above this a stamp is stale — a leftover from an earlier turn, not this one's. */
    private static final long MAX_PLAUSIBLE_MS = 60_000;

    private static final String UNKNOWN_PROVIDER = "unknown";

    private static final String[] STAGES = {"eou", "stt_final", "llm_ttft", "tts_ttfb", "first_audio"};

    /**
     * The same stages when nothing stamped the end of the utterance, so the total is
     * measured from the final instead. Named apart from {@code first_audio} on purpose:
     * it is a smaller number over a shorter span, and averaging the two together would
     * hide the endpointing silence this class exists to expose.
     */
    private static final String[] STAGES_AFTER_FINAL =
            {"eou", "stt_final", "llm_ttft", "tts_ttfb", "reply_wait"};

    private volatile long utteranceEndedAt;
    private volatile int eouWaitMs = -1;
    private volatile long finalAt;
    private volatile long llmRequestedAt;
    private volatile long llmFirstTokenAt;
    private volatile long ttsRequestedAt;
    private volatile long firstAudioAt;
    /** Whether this turn's reply was adopted from a speculation — llm_ttft is then a replay. */
    private volatile boolean speculated;
    /** The providers the call was set up with, so stages can be compared across vendors. */
    private volatile String sttProvider = UNKNOWN_PROVIDER;
    private volatile String ttsProvider = UNKNOWN_PROVIDER;
    /** The call's start, the zero of {@link TurnTiming}'s offsets; 0 = no timing kept. */
    private volatile long callStartedAtMillis;
    /** The last finished turn's timing, until {@link #takeTiming()} collects it. */
    private volatile TurnTiming lastTiming;

    /**
     * The STT and TTS providers chosen for this call. The choice, not necessarily the one
     * that served a given turn: a mid-call failover, or a catalog voice owned by another
     * vendor, still reports the chosen name.
     */
    public void providers(String stt, String tts) {
        this.sttProvider = stt == null || stt.isBlank() ? UNKNOWN_PROVIDER : stt;
        this.ttsProvider = tts == null || tts.isBlank() ? UNKNOWN_PROVIDER : tts;
    }

    /** The start of the call, which every {@link TurnTiming} offset is counted from. */
    public void callStartedAt(Instant startedAt) {
        this.callStartedAtMillis = startedAt != null ? startedAt.toEpochMilli() : 0;
    }

    /** The timing {@link #finish} took for the turn it closed, once; null when there is none. */
    public TurnTiming takeTiming() {
        TurnTiming timing = lastTiming;
        lastTiming = null;
        return timing;
    }

    /**
     * The gate shut: the caller stopped speaking {@code eouWaitMs} ago and the recognizer
     * has just been told so.
     */
    public void utteranceEnded(int eouWaitMs) {
        this.utteranceEndedAt = System.nanoTime();
        this.eouWaitMs = eouWaitMs;
        this.finalAt = 0;
    }

    /** The recognizer committed to what the caller said. */
    public void clientFinal() {
        this.finalAt = System.nanoTime();
    }

    /**
     * A turn is starting. Only the stamps this turn will take are cleared — the two in
     * front of it belong to the utterance that caused it and are what the turn is measured
     * from.
     */
    public void turnStarted() {
        llmRequestedAt = 0;
        llmFirstTokenAt = 0;
        ttsRequestedAt = 0;
        firstAudioAt = 0;
        speculated = false;
    }

    public void llmRequested() {
        llmRequestedAt = System.nanoTime();
    }

    /** First token of the reply. Ignored after the first one — a turn has one TTFT. */
    public void llmFirstToken() {
        if (llmFirstTokenAt == 0) {
            llmFirstTokenAt = System.nanoTime();
        }
    }

    /** The turn adopted a reply written during the endpointing silence. */
    public void speculationAdopted() {
        speculated = true;
    }

    /** The turn's first sentence went to a TTS provider. */
    public void ttsRequested() {
        if (ttsRequestedAt == 0) {
            ttsRequestedAt = System.nanoTime();
        }
    }

    /**
     * The first audio of the turn reached the endpoint. Records every stage that was
     * measured and returns the line to log, or {@code null} when this turn had nothing
     * worth reporting (the greeting, which no caller was waiting through).
     */
    public String finish(DialogMetrics metrics) {
        firstAudioAt = System.nanoTime();
        // With the recognizer doing its own endpointing (the default: no VAD gating, no
        // external endpointing) nothing closes a gate, so the end of the utterance is
        // never stamped — and with it the first three stages vanish and the line reports
        // only what the LLM and the TTS took. That reads as the whole wait and is not:
        // it left a caller's real three and a half seconds looking like two. Fall back to
        // the final, which is always stamped, and say so in the name.
        boolean fromUtterance = utteranceEndedAt != 0;
        String[] stages = fromUtterance ? STAGES : STAGES_AFTER_FINAL;
        long[] values = {
                eouWaitMs,
                millis(utteranceEndedAt, finalAt),
                speculated ? 0 : millis(llmRequestedAt, llmFirstTokenAt),
                millis(ttsRequestedAt, firstAudioAt),
                fromUtterance ? millis(utteranceEndedAt, firstAudioAt) : millis(finalAt, firstAudioAt)
        };
        boolean any = false;
        StringBuilder line = new StringBuilder("turn latency:");
        for (int i = 0; i < stages.length; i++) {
            if (values[i] < 0) {
                continue;
            }
            any = true;
            metrics.recordTurnStage(stages[i], sttProvider, ttsProvider, values[i]);
            line.append(' ').append(stages[i]).append('=').append(values[i]).append("ms");
        }
        if (!any) {
            return null;
        }
        if (speculated) {
            line.append(" (reply written ahead of the final)");
        }
        lastTiming = timing();
        // Consume the stamp: it belongs to the utterance this turn answered, and the next
        // turn's utterance gets its own or none at all. Left standing, it made eou report
        // the same frozen number every turn and stt_final grow by the whole gap between
        // turns — 1736ms, then 12719ms, then 28720ms on one call, none of them a wait
        // anybody sat through.
        utteranceEndedAt = 0;
        eouWaitMs = -1;
        return line.toString();
    }

    /**
     * The stamps as points on the call's clock. Taken in nanoTime, which has no epoch, so
     * each is placed by how long before the first audio it was — and the first audio by the
     * wall clock, now.
     */
    private TurnTiming timing() {
        if (callStartedAtMillis == 0) {
            return null;
        }
        Instant now = Instant.now();
        int firstAudioMs = (int) Math.max(0, now.toEpochMilli() - callStartedAtMillis);
        return new TurnTiming(now,
                offset(utteranceEndedAt, firstAudioMs),
                eouWaitMs >= 0 ? eouWaitMs : null,
                offset(finalAt, firstAudioMs),
                offset(llmRequestedAt, firstAudioMs),
                offset(llmFirstTokenAt, firstAudioMs),
                offset(ttsRequestedAt, firstAudioMs),
                firstAudioMs,
                speculated);
    }

    /** Where {@code stampNanos} falls on the call's clock; null when unstamped or stale. */
    private Integer offset(long stampNanos, int firstAudioMs) {
        long before = millis(stampNanos, firstAudioAt);
        return before < 0 || before > firstAudioMs ? null : (int) (firstAudioMs - before);
    }

    private static long millis(long fromNanos, long toNanos) {
        if (fromNanos == 0 || toNanos == 0 || toNanos < fromNanos) {
            return -1;
        }
        long ms = (toNanos - fromNanos) / 1_000_000;
        return ms > MAX_PLAUSIBLE_MS ? -1 : ms;
    }
}
