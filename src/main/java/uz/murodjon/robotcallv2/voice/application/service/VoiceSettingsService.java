package uz.murodjon.robotcallv2.voice.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.agent.tts.TtsProperties;
import uz.murodjon.robotcallv2.audit.application.port.input.AuditUseCase;
import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceSettingsUseCase;
import uz.murodjon.robotcallv2.voice.application.port.output.VoiceSettingsRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.service.VoiceSettingsValidator;

/**
 * Per-company TTS overrides (§11 settings).
 */
@Service
public class VoiceSettingsService implements VoiceSettingsUseCase {

    private final VoiceSettingsRepository voiceSettingsRepository;
    private final AuditUseCase auditService;
    private final TtsProperties ttsProperties;

    public VoiceSettingsService(VoiceSettingsRepository voiceSettingsRepository,
                                AuditUseCase auditService,
                                TtsProperties ttsProperties) {
        this.voiceSettingsRepository = voiceSettingsRepository;
        this.auditService = auditService;
        this.ttsProperties = ttsProperties;
    }

    @Override
    public VoiceSettings findByCompanyId(long companyId) {
        return voiceSettingsRepository.findByCompanyId(companyId);
    }

    @Override
    public VoiceSettings updateByCompanyId(long companyId, UpdateVoiceSettingsRequest request) {
        VoiceSettingsValidator.validateSpeed(request.speed());
        VoiceSettings row = voiceSettingsRepository.upsert(companyId, request.speed(), request.pitch());
        auditService.record(companyId, "VOICE_SETTINGS_UPDATE", "voice_settings", String.valueOf(row.companyId()), null);
        return row;
    }

    @Override
    public EffectiveVoiceSettings findEffectiveByCompanyId(long companyId) {
        String provider = ttsProperties.provider();
        VoiceSettings row = voiceSettingsRepository.findByCompanyId(companyId);
        return row == null ? new EffectiveVoiceSettings(provider, null, null, null)
                : new EffectiveVoiceSettings(provider, row.speed(), row.pitch(), null);
    }
}
