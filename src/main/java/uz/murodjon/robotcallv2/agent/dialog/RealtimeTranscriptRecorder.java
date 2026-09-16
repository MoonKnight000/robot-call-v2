package uz.murodjon.robotcallv2.agent.dialog;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.metrics.VoiceMetrics;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallAttemptUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.shared.dialog.Disposition;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What a realtime call said, written down: the transcript lines (stored, and put on the
 * live feed), and the after-the-fact fact-guard audit of what the engine already said.
 */
@Component
class RealtimeTranscriptRecorder {

    private static final Logger log = LoggerFactory.getLogger(RealtimeTranscriptRecorder.class);

    private final CallTranscriptUseCase callTranscriptUseCase;
    private final CallAttemptUseCase callAttemptUseCase;
    private final DialogProperties dialogProperties;
    private final VoiceMetrics metrics;
    private final DialogPolicy dialogPolicy;
    private final DialogTranscript transcript;

    /** Transcript inserts, off the provider WebSocket threads and in order — {@link #recordLine}. */
    private final ExecutorService transcriptWriter =
            Executors.newSingleThreadExecutor(Thread.ofVirtual().name("realtime-transcript").factory());

    RealtimeTranscriptRecorder(CallTranscriptUseCase callTranscriptUseCase, CallAttemptUseCase callAttemptUseCase,
                               DialogProperties dialogProperties, VoiceMetrics metrics,
                               DialogPolicy dialogPolicy, DialogTranscript transcript) {
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.callAttemptUseCase = callAttemptUseCase;
        this.dialogProperties = dialogProperties;
        this.metrics = metrics;
        this.dialogPolicy = dialogPolicy;
        this.transcript = transcript;
    }

    /**
     * Persist one transcript line. The bot's own lines go through {@link PiiRedactor} before
     * they are stored, as the cascade pipeline's do ({@code DialogTranscript}): a card or
     * passport number the engine read back is not something the archive keeps. Both sides'
     * lines are also published, redacted, to the live feed the operator screen is watching —
     * as the cascade pipeline publishes them ({@code DialogTranscript#publishClientLine}).
     *
     * <p>Called on the provider's WebSocket thread, so the database insert is handed to
     * {@link #transcriptWriter}: done inline, a slow database stalled that thread and with
     * it the bot's audio. One writer thread keeps the lines in the order they were spoken.
     */
    void recordLine(RealtimeDialogSession s, String role, String text) {
        String stored = "AGENT".equals(role) ? PiiRedactor.redact(text) : text;
        String published = "AGENT".equals(role) ? stored : PiiRedactor.redact(text);
        s.addTranscriptLine(role, stored);
        int offsetMs = (int) (Instant.now().toEpochMilli() - s.startedAt().toEpochMilli());
        var state = s.state();
        transcriptWriter.execute(() -> {
            callTranscriptUseCase.addTranscript(s.callAttemptId(), role, stored, state, offsetMs, null);
            transcript.publishLine(s.companyId(), s.channelId(), role, published, state);
        });
    }

    /**
     * Check what the engine already said against the call's facts (§4.4). Unlike the
     * cascade pipeline, which withholds a refused sentence, this can only record and — past
     * {@code fact-violation-escalate-after} — hand the call to an operator.
     */
    void auditFacts(RealtimeDialogSession s, String text) {
        List<String> bad = dialogPolicy.findFactViolations(text, s.scenario(), s.context(), s.toolFigures());
        if (bad.isEmpty()) {
            return;
        }
        int violations = s.recordFactViolation();
        metrics.factGuardSpoken();
        log.error("[{}] fact guard: figures {} were SPOKEN in {} but are not in the call facts ({}) "
                        + "— text was: {}",
                s.channelId(), bad, s.state(), s.context() != null ? s.context().facts() : null, text);
        callAttemptUseCase.recordError(s.callAttemptId(),
                "fact guard: spoken figures not in call facts: " + String.join(", ", bad));

        int limit = dialogProperties.factViolationEscalateAfter();
        if (limit <= 0 || violations < limit || s.isEnded()) {
            return;
        }
        log.error("[{}] escalating to an operator after {} spoken fact violation(s)",
                s.channelId(), violations);
        s.end(Disposition.TRANSFERRED);
    }

    @PreDestroy
    void shutdown() {
        transcriptWriter.shutdown(); // lets the lines already queued reach the database
    }
}
