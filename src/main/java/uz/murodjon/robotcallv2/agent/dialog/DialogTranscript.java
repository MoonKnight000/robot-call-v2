package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.live.application.port.input.LiveUseCase;
import uz.murodjon.robotcallv2.live.domain.entity.LiveTranscriptEvent;
import uz.murodjon.robotcallv2.live.domain.enums.LiveEventType;

import java.time.Duration;
import java.time.Instant;

/**
 * Writes what was said to the two places that need it: the stored transcript
 * ({@code call_attempt_transcript}) and the live feed the operator screen is watching
 * (§11.8), automatically redacting sensitive PII (card numbers, passports).
 */
@Component
public class DialogTranscript {

    private final CallTranscriptUseCase callTranscriptUseCase;
    private final LiveUseCase liveUseCase;

    public DialogTranscript(CallTranscriptUseCase callTranscriptUseCase, LiveUseCase liveUseCase) {
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.liveUseCase = liveUseCase;
    }

    /** Persist and publish one line the bot spoke. */
    public void recordAgentLine(DialogSession s, String line) {
        if (s.isLineGone()) {
            // The caller hung up while this turn was running; the call has been (or is being)
            // summarized already, and a line written now was never heard.
            return;
        }
        String safeLine = PiiRedactor.redact(line);
        callTranscriptUseCase.addTranscript(s.callAttemptId(), "AGENT", safeLine, s.state(), offsetMs(s), null);
        publishLine(s.companyId(), s.channelId(), "AGENT", safeLine, s.state());
    }

    /**
     * Publish one line the caller said.
     */
    public void publishClientLine(DialogSession s, String text) {
        String safeText = PiiRedactor.redact(text);
        publishLine(s.companyId(), s.channelId(), "CLIENT", safeText, s.state());
    }

    /**
     * Put one already-redacted line on the live feed (§11.8). Shared with the realtime
     * pipeline, whose lines are stored by {@code RealtimeTranscriptRecorder}.
     */
    void publishLine(long companyId, String channelId, String role, String safeText, String state) {
        liveUseCase.publish(companyId, LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(channelId, role, safeText, state));
    }

    /** Where in the recording this line falls, for the transcript player. */
    private static int offsetMs(DialogSession s) {
        return (int) Duration.between(s.startedAt(), Instant.now()).toMillis();
    }
}
