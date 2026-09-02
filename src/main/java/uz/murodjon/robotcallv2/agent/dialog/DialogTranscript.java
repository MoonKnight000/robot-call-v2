package uz.murodjon.robotcallv2.agent.dialog;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.callrecord.application.service.CallRecordService;
import uz.murodjon.robotcallv2.live.application.service.LiveBroadcastService;
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

    private final CallRecordService records;
    private final LiveBroadcastService broadcast;

    public DialogTranscript(CallRecordService records, LiveBroadcastService broadcast) {
        this.records = records;
        this.broadcast = broadcast;
    }

    /** Persist and publish one line the bot spoke. */
    public void recordAgentLine(DialogSession s, String line) {
        String safeLine = PiiRedactor.redact(line);
        records.addTranscript(s.callAttemptId(), "AGENT", safeLine, s.state(), offsetMs(s), null);
        broadcast.publish(LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(s.channelId(), "AGENT", safeLine, s.state()));
    }

    /**
     * Publish one line the caller said.
     */
    public void publishClientLine(DialogSession s, String text) {
        String safeText = PiiRedactor.redact(text);
        broadcast.publish(LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(s.channelId(), "CLIENT", safeText, s.state()));
    }

    /** Where in the recording this line falls, for the transcript player. */
    private static int offsetMs(DialogSession s) {
        return (int) Duration.between(s.startedAt(), Instant.now()).toMillis();
    }
}
