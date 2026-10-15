package uz.murodjon.robotcallv2.voice.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.shared.api.UploadedFile;
import uz.murodjon.robotcallv2.shared.exception.ConflictException;
import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.NotFoundException;
import uz.murodjon.robotcallv2.voice.application.dto.CloneVoiceRequest;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceCloneUseCase;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.application.port.output.VoiceClonerPort;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;
import uz.murodjon.robotcallv2.voice.domain.service.VoiceCloneValidator;

import java.util.List;
import java.util.Locale;

/**
 * A company's own voice — its best operator, its brand voice — made from a few recordings
 * and then picked for an agent like any catalog voice. Only that company sees or speaks it
 * ({@link TtsVoice#isVisibleTo}).
 */
@Service
public class VoiceCloneService implements VoiceCloneUseCase {

    private static final Logger log = LoggerFactory.getLogger(VoiceCloneService.class);

    /** Each clone takes a slot of the platform's vendor subscription, which all companies share. */
    static final int MAX_CLONES_PER_COMPANY = 5;

    private final TtsVoiceRepository ttsVoiceRepository;
    private final VoiceClonerPort voiceClonerPort;
    private final AuditUseCase auditUseCase;

    public VoiceCloneService(TtsVoiceRepository ttsVoiceRepository, VoiceClonerPort voiceClonerPort,
                             AuditUseCase auditUseCase) {
        this.ttsVoiceRepository = ttsVoiceRepository;
        this.voiceClonerPort = voiceClonerPort;
        this.auditUseCase = auditUseCase;
    }

    @Override
    public TtsVoice cloneVoice(long companyId, CloneVoiceRequest request, List<UploadedFile> samples) {
        VoiceCloneValidator.validate(request.consent(), samples);
        if (!voiceClonerPort.isAvailable()) {
            throw new ConflictException(ErrorCode.VOICE_CLONE_UNAVAILABLE);
        }
        if (ttsVoiceRepository.countActiveByCompanyId(companyId) >= MAX_CLONES_PER_COMPANY) {
            throw new ConflictException(ErrorCode.VOICE_CLONE_LIMIT_REACHED, MAX_CLONES_PER_COMPANY);
        }
        // The company id in the vendor-side name tells the clones apart in the vendor's dashboard.
        String providerVoiceId = voiceClonerPort.createClone(companyId + " · " + request.name().trim(), samples);
        TtsVoice voice = new TtsVoice("clone-" + companyId + "-" + providerVoiceId.toLowerCase(Locale.ROOT),
                voiceClonerPort.findProviderName(), request.language(), providerVoiceId, request.name().trim(), null,
                companyId);
        ttsVoiceRepository.create(voice);
        auditUseCase.record(companyId, "VOICE_CLONE", "tts_voice", voice.id(),
                voice.label() + " (" + voice.language() + ", " + samples.size() + " recordings, speaker consent confirmed)");
        log.info("Company {} cloned voice {} ({} recordings)", companyId, voice.id(), samples.size());
        return voice;
    }

    /**
     * Removes the clone at the vendor and switches it off here; the row stays, since agents and
     * past calls name it. An agent still set to it can no longer be spoken in that voice: its
     * calls go to the TTS fallback provider if one is configured — switch such agents first.
     */
    @Override
    public void deleteClonedVoice(long companyId, String voiceId) {
        TtsVoice voice = ttsVoiceRepository.findActiveById(voiceId);
        // A shared voice, or another company's clone, is "not found" to this company.
        if (voice == null || voice.companyId() == null || voice.companyId() != companyId) {
            throw new NotFoundException(ErrorCode.TTS_VOICE_NOT_FOUND, voiceId);
        }
        voiceClonerPort.deleteClone(voice.name());
        ttsVoiceRepository.deactivate(voice.id());
        auditUseCase.record(companyId, "VOICE_CLONE_DELETE", "tts_voice", voice.id(), voice.label());
    }
}
