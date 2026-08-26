package uz.murodjon.uysotvoice.voice.repository;

import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.company.service.CurrentCompany;
import uz.murodjon.uysotvoice.voice.dto.VoiceSettings;
import uz.murodjon.uysotvoice.voice.entity.VoiceSettingsEntity;

import java.time.Instant;

/** JPA-backed DAO for {@code voice_settings} (§11 settings) — one row per company. */
@Repository
public class VoiceSettingsRepository {

    private final VoiceSettingsJpaRepository jpa;
    private final CurrentCompany company;

    public VoiceSettingsRepository(VoiceSettingsJpaRepository jpa, CurrentCompany company) {
        this.jpa = jpa;
        this.company = company;
    }

    /** {@code null} if the current company has never set an override — every field falls back to the process default. */
    public VoiceSettings find() {
        return jpa.findByCompanyId(company.id()).map(VoiceSettingsRepository::toRow).orElse(null);
    }

    /** Also used by {@code TtsRouter}'s caller to resolve a specific company's overrides at call time. */
    public VoiceSettings find(long companyId) {
        return jpa.findByCompanyId(companyId).map(VoiceSettingsRepository::toRow).orElse(null);
    }

    /** Upsert: the current company's row is created on first {@code PUT}, updated after. */
    public VoiceSettings save(Double speed, Double pitch) {
        long companyId = company.id();
        VoiceSettingsEntity entity = jpa.findByCompanyId(companyId).orElseGet(() -> {
            VoiceSettingsEntity fresh = new VoiceSettingsEntity();
            fresh.setCompanyId(companyId);
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setSpeed(speed);
        entity.setPitch(pitch);
        return toRow(jpa.save(entity));
    }

    private static VoiceSettings toRow(VoiceSettingsEntity e) {
        return new VoiceSettings(e.getCompanyId(), e.getSpeed(), e.getPitch(), e.getCreatedAt());
    }
}
