package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.agent.audio.AudioListener;
import uz.murodjon.robotcallv2.agent.dialog.PiiRedactor;
import uz.murodjon.robotcallv2.agent.metrics.SttMetrics;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.stt.SttProperties;
import uz.murodjon.robotcallv2.agent.stt.SttProvider;
import uz.murodjon.robotcallv2.agent.stt.SttProviderSelector;
import uz.murodjon.robotcallv2.agent.stt.SttStreamBridge;
import uz.murodjon.robotcallv2.callrecord.application.port.input.CallTranscriptUseCase;
import uz.murodjon.robotcallv2.company.domain.enums.Language;

import java.time.Instant;
import java.util.List;

/**
 * Transcribes one side of a client's call with an operator as it happens
 * (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §2.2): a recognizer on that side's tap, its finals
 * written to the call's transcript under the speaker's role and the moment they were heard.
 * Both sides land in the same transcript, so ordering by time is all the merging there is.
 *
 * <p>The operator's lines are redacted as the bot's are — they read numbers out to the
 * client; the client's are kept as said, as on an AI call.
 */
@Component
class OperatorCallTranscriber {

    static final String CLIENT = "CLIENT";
    static final String OPERATOR = "OPERATOR";

    /** What a call without an agent is recognised in: the platform's two languages, Uzbek first. */
    private static final List<String> OPERATOR_CALL_LANGUAGES = List.of(Language.UZ_UZ.code(), Language.RU_RU.code());

    private static final Logger log = LoggerFactory.getLogger(OperatorCallTranscriber.class);

    private final SttProperties sttProperties;
    private final SttProviderSelector sttProviderSelector;
    private final SttMetrics sttMetrics;
    private final CallTranscriptUseCase callTranscriptUseCase;
    private final AriEventLanes ariEventLanes;

    OperatorCallTranscriber(SttProperties sttProperties, SttProviderSelector sttProviderSelector, SttMetrics sttMetrics,
                            CallTranscriptUseCase callTranscriptUseCase, AriEventLanes ariEventLanes) {
        this.sttProperties = sttProperties;
        this.sttProviderSelector = sttProviderSelector;
        this.sttMetrics = sttMetrics;
        this.callTranscriptUseCase = callTranscriptUseCase;
        this.ariEventLanes = ariEventLanes;
    }

    /**
     * A recognizer for {@code role}'s side of the call, to sit on that side's tap; null when
     * STT is off or cannot start — the call is then recorded, just not transcribed.
     */
    AudioListener openFor(CallSession session, String role) {
        if (!sttProperties.enabled()) {
            return null;
        }
        String channelId = session.channelId();
        try {
            SttProvider provider = sttProviderSelector.findForCall(sttProperties.provider());
            if (provider == null) {
                return null;
            }
            List<String> languages = session.agent() != null ? session.agent().findSpokenLanguages()
                    : OPERATOR_CALL_LANGUAGES;
            String language = session.agent() != null ? session.agent().language() : languages.getFirst();
            return new SttStreamBridge(provider, provider.sampleRate(), CallMediaFactory.SAMPLE_RATE,
                    channelId + "-" + role.toLowerCase(), language, languages, (text, isFinal, confidence) -> {
                        if (!isFinal || text == null || text.isBlank()) {
                            return;
                        }
                        int offsetMs = (int) (Instant.now().toEpochMilli() - session.startedAt().toEpochMilli());
                        String stored = PiiRedactor.redact(text);
                        // Off the vendor's callback thread; the call's lane keeps both sides in the order heard.
                        ariEventLanes.execute(channelId, () -> callTranscriptUseCase.addTranscript(
                                session.callAttemptId(), role, stored, null, offsetMs, confidence));
                    }, null, sttMetrics, null, sttProperties.responseTimeoutMs(), null, List.of(), sttProviderSelector,
                    null, sttProperties.stream());
        } catch (Exception e) {
            log.warn("[{}] the {} side will not be transcribed: {}", channelId, role, e.getMessage());
            return null;
        }
    }
}
