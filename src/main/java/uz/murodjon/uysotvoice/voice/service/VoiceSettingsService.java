package uz.murodjon.uysotvoice.voice.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
import uz.murodjon.uysotvoice.engine.service.EngineConfigService;
import uz.murodjon.uysotvoice.voice.dto.EffectiveVoiceSettings;
import uz.murodjon.uysotvoice.voice.dto.UpdateVoiceSettingsRequest;
import uz.murodjon.uysotvoice.voice.dto.VoiceSettings;
import uz.murodjon.uysotvoice.voice.repository.VoiceSettingsRepository;

/**
 * Per-company TTS overrides (§11 settings) — how much of {@code voice-agent.tts.*} a
 * company may override for its own calls. {@link #effective} is the runtime hot path
 * {@code DialogEngine} calls once per call; the rest is admin CRUD.
 */
@Service
public class VoiceSettingsService {

    private final VoiceSettingsRepository repo;
    private final AuditService audit;
    private final EngineConfigService engineConfigService;

    public VoiceSettingsService(VoiceSettingsRepository repo, AuditService audit,
                                EngineConfigService engineConfigService) {
        this.repo = repo;
        this.audit = audit;
        this.engineConfigService = engineConfigService;
    }

    /** The current company's row, or {@code null} if it has never overridden anything. */
    public VoiceSettings find() {
        return repo.find();
    }

    public VoiceSettings update(UpdateVoiceSettingsRequest r) {
        VoiceSettings row = repo.save(r.speed(), r.pitch());
        audit.record("VOICE_SETTINGS_UPDATE", "voice_settings", String.valueOf(row.companyId()), null);
        return row;
    }

    /**
     * Everything {@code TtsRouter} needs for {@code companyId}'s calls — called once per
     * call from {@code DialogEngine.startCall} and reused for the whole call. Never
     * throws: a company that overrode nothing simply speaks with the process defaults.
     *
     * <p>Which provider speaks is an {@code engine_config} setting, not a voice one, but
     * it is folded in here because {@link EffectiveVoiceSettings} is the single object
     * that reaches the router — leaving it out would mean every call site resolving the
     * engine separately and remembering to attach it.
     */
    public EffectiveVoiceSettings effective(long companyId) {
        String provider = engineConfigService.findEffectiveByCompanyId(companyId).ttsProvider();
        VoiceSettings row = repo.find(companyId);
        // role stays null here: it belongs to the voice, and which voice this call uses
        // is only known once TtsRouter has resolved it.
        return row == null ? new EffectiveVoiceSettings(provider, null, null, null)
                : new EffectiveVoiceSettings(provider, row.speed(), row.pitch(), null);
    }
}
