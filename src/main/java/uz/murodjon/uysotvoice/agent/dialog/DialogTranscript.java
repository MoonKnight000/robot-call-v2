package uz.murodjon.uysotvoice.agent.dialog;

import org.springframework.stereotype.Component;

import uz.murodjon.uysotvoice.callrecord.service.CallRecordService;
import uz.murodjon.uysotvoice.live.dto.LiveTranscriptEvent;
import uz.murodjon.uysotvoice.live.enums.LiveEventType;
import uz.murodjon.uysotvoice.live.service.LiveBroadcastService;

import java.time.Duration;
import java.time.Instant;

/**
 * Writes what was said to the two places that need it: the stored transcript
 * ({@code call_attempt_transcript}) and the live feed the operator screen is watching
 * (§11.8).
 *
 * <p>One class rather than two calls at each site because every site needs both, and
 * because {@link CallRecordService#addTranscript} has no {@code channelId} to key a live
 * event on — only the session does.
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
        records.addTranscript(s.callAttemptId(), "AGENT", line, s.state(), offsetMs(s), null);
        broadcast.publish(LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(s.channelId(), "AGENT", line, s.state()));
    }

    /**
     * Publish one line the caller said.
     *
     * <p>Published only, not persisted: the caller's own transcripts are written by the
     * STT path that produced them, and the operator should see what was said whether or
     * not the bot treats it as its cue to speak.
     */
    public void publishClientLine(DialogSession s, String text) {
        broadcast.publish(LiveEventType.TRANSCRIPT,
                new LiveTranscriptEvent(s.channelId(), "CLIENT", text, s.state()));
    }

    /** Where in the recording this line falls, for the transcript player. */
    private static int offsetMs(DialogSession s) {
        return (int) Duration.between(s.startedAt(), Instant.now()).toMillis();
    }
}
