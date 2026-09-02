package uz.murodjon.robotcallv2.voice.application.service;

import org.springframework.stereotype.Service;

import uz.murodjon.robotcallv2.audit.application.service.AuditService;
import uz.murodjon.robotcallv2.company.application.service.CurrentCompany;
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
    private final CurrentCompany currentCompany;

    public VoiceSettingsService(VoiceSettingsRepository voiceSettingsRepository,
                                AuditService auditService,
                                EngineConfigService engineConfigService,
                                CurrentCompany currentCompany) {
        this.voiceSettingsRepository = voiceSettingsRepository;
        this.auditService = auditService;
        this.engineConfigService = engineConfigService;
        this.currentCompany = currentCompany;
    }

    @Override
    public VoiceSettings find() {
        return voiceSettingsRepository.findByCompanyId(currentCompany.id());
    }

    @Override
    public VoiceSettings update(UpdateVoiceSettingsRequest r) {
        long companyId = currentCompany.id();
        VoiceSettings row = voiceSettingsRepository.upsert(companyId, r.speed(), r.pitch());
        auditService.record("VOICE_SETTINGS_UPDATE", "voice_settings", String.valueOf(row.companyId()), null);
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
