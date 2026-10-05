package uz.murodjon.robotcallv2.agent.ari;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.agent.audio.LiveAudioMonitor;
import uz.murodjon.robotcallv2.agent.dialog.DialogRouter;
import uz.murodjon.robotcallv2.agent.dialog.LiveDialogSnapshot;
import uz.murodjon.robotcallv2.agent.routing.CallRouteRegistry;
import uz.murodjon.robotcallv2.agent.rtp.RtpProperties;
import uz.murodjon.robotcallv2.agent.rtp.WavAudio;
import uz.murodjon.robotcallv2.agent.rtp.WavHeader;
import uz.murodjon.robotcallv2.agent.rtp.WavReader;
import uz.murodjon.robotcallv2.agent.session.CallSession;
import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.agent.tts.TtsRouter;
import uz.murodjon.robotcallv2.callrecord.application.dto.LiveCallRow;
import uz.murodjon.robotcallv2.callrecord.application.dto.PlayResponse;
import uz.murodjon.robotcallv2.callrecord.application.dto.SayResponse;
import uz.murodjon.robotcallv2.campaign.application.port.input.CampaignUseCase;
import uz.murodjon.robotcallv2.campaign.domain.entity.Campaign;
import uz.murodjon.robotcallv2.dialer.application.dto.OutboundCall;
import uz.murodjon.robotcallv2.shared.api.AudioStreamWriter;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * What the API can do with, and read about, a call in progress on this instance: play a
 * file or a sentence into it, listen in, list the live calls, and the checks that a call is
 * live and belongs to the asking company.
 */
@Service
class ActiveCallService {

    private static final Logger log = LoggerFactory.getLogger(ActiveCallService.class);

    /** Enough for any realistic prompt; stops one request from running up a TTS bill. */
    private static final int MAX_SAY_CHARS = 1000;

    private final CallSessionRegistry callSessionRegistry;
    private final PendingCallRegistry pendingCallRegistry;
    private final DialogRouter dialogRouter;
    private final CampaignUseCase campaignUseCase;
    private final CallRouteRegistry routeRegistry;
    private final TtsProperties ttsProperties;
    private final TtsRouter ttsRouter;
    private final RtpProperties rtpProperties;

    ActiveCallService(CallSessionRegistry callSessionRegistry,
                      PendingCallRegistry pendingCallRegistry,
                      DialogRouter dialogRouter,
                      CampaignUseCase campaignUseCase,
                      CallRouteRegistry routeRegistry,
                      TtsProperties ttsProperties,
                      TtsRouter ttsRouter,
                      RtpProperties rtpProperties) {
        this.callSessionRegistry = callSessionRegistry;
        this.pendingCallRegistry = pendingCallRegistry;
        this.dialogRouter = dialogRouter;
        this.campaignUseCase = campaignUseCase;
        this.routeRegistry = routeRegistry;
        this.ttsProperties = ttsProperties;
        this.ttsRouter = ttsRouter;
        this.rtpProperties = rtpProperties;
    }

    void play(String channelId, Path file) {
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        try {
            WavAudio audio = WavReader.read(file);
            if (audio.sampleRate() != CallMediaFactory.SAMPLE_RATE) {
                log.warn("Playback file {} is {} Hz, expected {} Hz — audio may sound wrong",
                        file, audio.sampleRate(), CallMediaFactory.SAMPLE_RATE);
            }
            session.endpoint().playPcm(audio.samples());
        } catch (IOException e) {
            throw new ExternalServiceException(ErrorCode.ARI_PLAYBACK_FAILED, "asterisk", e, file, e.getMessage());
        }
    }

    PlayResponse playRecording(String channelId, String file) {
        Path resolved = resolveInRecordingDir(file);
        play(channelId, resolved);
        return new PlayResponse(channelId, resolved.toString(), "playing");
    }

    private Path resolveInRecordingDir(String file) {
        if (file == null || file.isBlank()) {
            throw new ValidationException(ErrorCode.PLAYBACK_FILE_BLANK);
        }
        Path base = Path.of(rtpProperties.recordingDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(file).normalize();
        if (!resolved.startsWith(base)) {
            throw new ValidationException(ErrorCode.PLAYBACK_FILE_OUTSIDE_BASE, base);
        }
        try {
            Path real = resolved.toRealPath();
            if (!real.startsWith(base.toRealPath())) {
                throw new ValidationException(ErrorCode.PLAYBACK_FILE_OUTSIDE_BASE, base);
            }
            return real;
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.PLAYBACK_FILE_NOT_FOUND, file);
        }
    }

    void speak(String channelId, String text, String language) {
        speak(channelId, text, language, null);
    }

    void speak(String channelId, String text, String language, String ttsVoice) {
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        if (!ttsProperties.enabled()) {
            throw new ConflictException(ErrorCode.TTS_DISABLED);
        }
        String lang = (language == null || language.isBlank()) ? ttsProperties.defaultLanguage() : language;
        short[] pcm = ttsRouter.synthesize(text, lang, ttsVoice);
        log.info("TTS speak [{}] lang={} voice={} chars={} -> {} samples",
                channelId, lang, ttsVoice != null ? ttsVoice : "default", text.length(), pcm.length);
        session.endpoint().playPcm(pcm);
    }

    SayResponse say(String channelId, String text, String language, String voice) {
        if (text.isBlank()) {
            throw new ValidationException(ErrorCode.SAY_TEXT_BLANK);
        }
        if (text.length() > MAX_SAY_CHARS) {
            throw new ValidationException(ErrorCode.SAY_TEXT_TOO_LONG, MAX_SAY_CHARS);
        }
        speak(channelId, text, language, voice);
        return new SayResponse(channelId, text, language, "speaking");
    }

    AudioStreamWriter listen(String channelId) {
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        LiveAudioMonitor monitor = session.audioMonitor();
        return output -> {
            BlockingQueue<byte[]> queue = monitor.subscribe();
            try {
                output.write(WavHeader.bytes(CallMediaFactory.SAMPLE_RATE, 1, Integer.MAX_VALUE - WavHeader.SIZE));
                output.flush();
                while (true) {
                    byte[] chunk;
                    try {
                        chunk = queue.poll(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    if (chunk == null) {
                        continue;
                    }
                    if (chunk == LiveAudioMonitor.EOF) {
                        break;
                    }
                    output.write(chunk);
                    output.flush();
                }
            } finally {
                monitor.unsubscribe(queue);
            }
        };
    }

    /** The calls of {@code companyId} still in conversation — never another tenant's. */
    List<LiveCallRow> findLiveCalls(long companyId) {
        return dialogRouter.liveDialogs().stream()
                .filter(s -> isActiveCallOf(companyId, s.channelId()))
                .map(this::toLiveCallRow)
                .toList();
    }

    private LiveCallRow toLiveCallRow(LiveDialogSnapshot s) {
        OutboundCall outbound = pendingCallRegistry.findOutboundProfile(s.channelId());
        Long campaignId = outbound != null ? outbound.campaignId() : null;
        String phone = outbound != null ? outbound.phone() : null;
        String campaignName = null;
        if (campaignId != null) {
            Campaign campaign = campaignUseCase.findCampaign(outbound.companyId(), campaignId);
            campaignName = campaign != null ? campaign.name() : null;
        }
        CallSession session = callSessionRegistry.find(s.channelId());
        String trunk = session != null ? session.trunk() : null;
        return new LiveCallRow(s.channelId(), phone, s.clientName(), campaignId, campaignName,
                s.language(), s.startedAt(), s.dialogState(), trunk);
    }

    /** Whether {@code channelId} is a live call of {@code companyId}. */
    boolean isActiveCallOf(long companyId, String channelId) {
        CallSession session = callSessionRegistry.find(channelId);
        return session != null && session.companyId() == companyId;
    }

    /**
     * Throws unless {@code channelId} is a live call of {@code companyId} on this instance.
     * A call of the company that another instance holds answers {@code 409
     * CALL_ON_OTHER_INSTANCE} naming that instance, so the request can be sent there.
     */
    void requireLiveCallOf(long companyId, String channelId) {
        if (isActiveCallOf(companyId, channelId)) {
            return;
        }
        String owner = routeRegistry.findInstanceOf(companyId, channelId);
        if (owner != null && !owner.equals(routeRegistry.instanceId())) {
            throw new ConflictException(ErrorCode.CALL_ON_OTHER_INSTANCE, channelId, owner);
        }
        throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
    }

    long callAttemptIdOf(String channelId) {
        CallSession session = callSessionRegistry.find(channelId);
        return session != null ? session.callAttemptId() : 0;
    }

    String bridgeOf(String channelId) {
        CallSession session = callSessionRegistry.find(channelId);
        if (session == null) {
            throw new ConflictException(ErrorCode.CALL_NOT_ACTIVE, channelId);
        }
        return session.bridgeId();
    }
}
