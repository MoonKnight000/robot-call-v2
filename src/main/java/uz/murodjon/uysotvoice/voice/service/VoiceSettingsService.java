package uz.murodjon.uysotvoice.voice.service;

import org.springframework.stereotype.Service;

import uz.murodjon.uysotvoice.audit.service.AuditService;
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

    public VoiceSettingsService(VoiceSettingsRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    /** The current company's row, or {@code null} if it has never overridden anything. */
    public VoiceSettings find() {
        return repo.find();
    }

    public VoiceSettings update(UpdateVoiceSettingsRequest r) {
        VoiceSettings row = repo.save(r.provider(), r.speed(), r.pitch());
        audit.record("VOICE_SETTINGS_UPDATE", "voice_settings", String.valueOf(row.companyId()), r.provider());
        return row;
    }

    /**
     * {@code companyId}'s overrides — called once per call from {@code DialogEngine.startCall}
     * and reused for the whole call. Never throws: a company with no row simply gets
     * {@link EffectiveVoiceSettings#NONE} (the process's own configured routing).
     */
    public EffectiveVoiceSettings effective(long companyId) {
        VoiceSettings row = repo.find(companyId);
        return row == null ? EffectiveVoiceSettings.NONE
                // role stays null here: it belongs to the voice, and which voice this call
                // uses is only known once TtsRouter has resolved it.
                : new EffectiveVoiceSettings(row.provider(), row.speed(), row.pitch(), null);
    }
}
