package uz.murodjon.robotcallv2.voice.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.engine.application.service.EngineConfigService;
import uz.murodjon.robotcallv2.voice.application.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.robotcallv2.voice.application.port.input.VoiceSettingsUseCase;
import uz.murodjon.robotcallv2.voice.application.port.output.VoiceSettingsRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.EffectiveVoiceSettings;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;

/**
 * Per-company TTS overrides (§11 settings).
 */
@Service
public class VoiceSettingsService implements VoiceSettingsUseCase {

    private final VoiceSettingsRepository voiceSettingsRepository;
    private final AuditService auditService;
    private final EngineConfigService engineConfigService;

    public VoiceSettingsService(VoiceSettingsRepository voiceSettingsRepository,
                                AuditService auditService,
                                EngineConfigService engineConfigService) {
        this.voiceSettingsRepository = voiceSettingsRepository;
        this.auditService = auditService;
        this.engineConfigService = engineConfigService;
    }

    @Override
    public VoiceSettings findByCompanyId(long companyId) {
        return voiceSettingsRepository.findByCompanyId(companyId);
    }

    @Override
    public VoiceSettings updateByCompanyId(long companyId, UpdateVoiceSettingsRequest request) {
        VoiceSettings row = voiceSettingsRepository.upsert(companyId, request.speed(), request.pitch());
        auditService.record(companyId, "VOICE_SETTINGS_UPDATE", "voice_settings", String.valueOf(row.companyId()), null);
        return row;
    }

    @Override
    public EffectiveVoiceSettings effective(long companyId) {
        String provider = engineConfigService.findEffectiveByCompanyId(companyId).ttsProvider();
        VoiceSettings row = voiceSettingsRepository.findByCompanyId(companyId);
        return row == null ? new EffectiveVoiceSettings(provider, null, null, null)
                : new EffectiveVoiceSettings(provider, row.speed(), row.pitch(), null);
    }
}
