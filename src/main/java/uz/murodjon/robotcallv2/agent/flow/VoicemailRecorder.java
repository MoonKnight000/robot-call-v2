package uz.murodjon.robotcallv2.agent.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.dialog.PiiRedactor;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProvider;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallEventUseCase;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.callrecord.domain.enums.CallEventType;
import uz.murodjon.robotcallv2.voicemail.application.port.input.VoicemailUseCase;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailMessage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * Takes a voicemail (docs/INBOUND-PBX-PLAN.md §8.1): greeting, beep, then the caller's own
 * words until they press #, fall silent, reach the box's limit or hang up. When the box
 * transcribes, a recognizer listens along, so the operator reads the message instead of
 * playing it — and the call's summary covers it, since the words also land in the
 * call's transcript.
 */
@Component
public class VoicemailRecorder {

    private static final Logger log = LoggerFactory.getLogger(VoicemailRecorder.class);
    private static final int SAMPLE_RATE = 8000;
    /** Silence after speech that ends the message. */
    private static final long SILENCE_END_MS = 5000;
    /** Nothing said this long after the beep: the caller is not leaving a message. */
    private static final int NOTHING_SAID_SECONDS = 10;
    /** How long the recognizer gets to finalize the last words after recording stops. */
    private static final long TRAILING_TRANSCRIPT_MS = 1500;
    /** Under a second of audio is a cough, not a message. */
    private static final int MIN_MESSAGE_SAMPLES = SAMPLE_RATE;

    private final FlowPrompter prompter;
    private final VoicemailUseCase voicemailUseCase;
    private final SttProviderSelector sttProviderSelector;
    private final SttProperties sttProperties;
    private final SttMetrics metrics;
    private final CallEventUseCase callEventUseCase;
    private final CallTranscriptUseCase callTranscriptUseCase;

    public VoicemailRecorder(FlowPrompter prompter, VoicemailUseCase voicemailUseCase,
                             SttProviderSelector sttProviderSelector, SttProperties sttProperties, SttMetrics metrics,
                             CallEventUseCase callEventUseCase, CallTranscriptUseCase callTranscriptUseCase) {
        this.prompter = prompter;
        this.voicemailUseCase = voicemailUseCase;
        this.sttProviderSelector = sttProviderSelector;
        this.sttProperties = sttProperties;
        this.metrics = metrics;
        this.callEventUseCase = callEventUseCase;
        this.callTranscriptUseCase = callTranscriptUseCase;
    }

    /** The saved message's id, or null when the box is gone or nothing was said. */
    public Long record(FlowCall call, long boxId) {
        VoicemailBox box = voicemailUseCase.findBox(call.companyId(), boxId);
        if (box == null || !box.enabled()) {
            log.warn("[{}] voicemail box {} is missing or disabled — no message taken", call.channelId(), boxId);
            return null;
        }
        short[] greeting = box.greetingFileId() != null
                ? prompter.recording(call.companyId(), box.greetingFileId())
                : prompter.speech(box.greetingText() != null ? box.greetingText()
                : PbxPhrases.voicemailGreeting(call.language()), call.language());
        prompter.play(call, greeting, false);
        if (call.isGone()) {
            return null;
        }
        prompter.play(call, prompter.beep(), false);
        call.digits().clear();

        PcmCapture capture = new PcmCapture(box.maxSeconds());
        StringBuffer transcript = new StringBuffer();
        SttStreamBridge recognizer = box.transcribe() ? openRecognizer(call, transcript) : null;
        call.attachListener(capture);
        if (recognizer != null) {
            call.attachListener(recognizer);
        }
        try {
            while (!call.isGone() && !capture.full()) {
                Character key = call.digits().poll(200, call::isGone);
                if (key != null && key == '#') {
                    break;
                }
                if (capture.heardSpeech() && capture.silentMillis() > SILENCE_END_MS) {
                    break;
                }
                if (!capture.heardSpeech() && capture.seconds() >= NOTHING_SAID_SECONDS) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            call.detachListener(capture);
            if (recognizer != null) {
                sleepQuietly(TRAILING_TRANSCRIPT_MS);
                call.detachListener(recognizer);
                recognizer.close();
            }
        }

        short[] pcm = capture.samples();
        if (!capture.heardSpeech() || pcm.length < MIN_MESSAGE_SAMPLES) {
            log.info("[{}] nothing was said after the beep — no voicemail saved", call.channelId());
            return null;
        }
        String text = PiiRedactor.redact(transcript.toString().trim());
        int seconds = pcm.length / SAMPLE_RATE;
        VoicemailMessage message = voicemailUseCase.recordMessage(call.companyId(), boxId,
                call.callAttemptId() != 0 ? call.callAttemptId() : null, call.callerNumber(), wav(pcm), seconds, text);
        if (!text.isEmpty()) {
            int offsetMs = (int) (System.currentTimeMillis() - call.startedAt().toEpochMilli());
            callTranscriptUseCase.addTranscript(call.callAttemptId(), "CLIENT", text, "VOICEMAIL", offsetMs, 1f);
        }
        callEventUseCase.record(call.companyId(), call.callAttemptId(), CallEventType.VOICEMAIL,
                "boxId", boxId, "seconds", seconds);
        if (!call.isGone()) {
            prompter.say(call, PbxPhrases.voicemailSaved(call.language()), false);
        }
        return message.id();
    }

    private SttStreamBridge openRecognizer(FlowCall call, StringBuffer transcript) {
        if (!sttProperties.enabled()) {
            return null;
        }
        try {
            SttProvider provider = sttProviderSelector.findForCall(null);
            if (provider == null) {
                return null;
            }
            return new SttStreamBridge(provider, provider.sampleRate(), SAMPLE_RATE, call.channelId(), call.language(),
                    List.of(), (text, isFinal, confidence) -> {
                        if (isFinal && text != null && !text.isBlank()) {
                            synchronized (transcript) {
                                if (!transcript.isEmpty()) {
                                    transcript.append(' ');
                                }
                                transcript.append(text.trim());
                            }
                        }
                    }, null, metrics, null, sttProperties.responseTimeoutMs(), null, List.of(), sttProviderSelector, null, sttProperties.stream());
        } catch (Exception e) {
            log.warn("[{}] voicemail will not be transcribed: {}", call.channelId(), e.getMessage());
            return null;
        }
    }

    static byte[] wav(short[] pcm) {
        byte[] header = WavHeader.bytes(SAMPLE_RATE, 1, pcm.length * 2);
        ByteBuffer buffer = ByteBuffer.allocate(header.length + pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(header);
        for (short sample : pcm) {
            buffer.putShort(sample);
        }
        return buffer.array();
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
