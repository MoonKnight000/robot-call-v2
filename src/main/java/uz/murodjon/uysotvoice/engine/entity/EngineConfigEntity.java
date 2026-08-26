package uz.murodjon.uysotvoice.engine.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uz.murodjon.uysotvoice.engine.enums.PipelineMode;

import java.time.Instant;

/**
 * JPA entity for {@code engine_config} (§11 settings) — one row per company, holding
 * which speech engine that company's calls run on.
 *
 * <p>Kept out of {@code voice_settings} (which stays about how a voice sounds — speed,
 * pitch) because the engine choice also decides whether there is a separate TTS step at
 * all: a {@link PipelineMode#REALTIME} engine speaks for itself.
 */
@Entity
@Table(name = "engine_config")
public class EngineConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private long companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineMode mode;

    @Column(name = "stt_provider", length = 50)
    private String sttProvider;

    @Column(name = "tts_provider", length = 50)
    private String ttsProvider;

    @Column(name = "realtime_provider", length = 50)
    private String realtimeProvider;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(long companyId) {
        this.companyId = companyId;
    }

    public PipelineMode getMode() {
        return mode;
    }

    public void setMode(PipelineMode mode) {
        this.mode = mode;
    }

    public String getSttProvider() {
        return sttProvider;
    }

    public void setSttProvider(String sttProvider) {
        this.sttProvider = sttProvider;
    }

    public String getTtsProvider() {
        return ttsProvider;
    }

    public void setTtsProvider(String ttsProvider) {
        this.ttsProvider = ttsProvider;
    }

    public String getRealtimeProvider() {
        return realtimeProvider;
    }

    public void setRealtimeProvider(String realtimeProvider) {
        this.realtimeProvider = realtimeProvider;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
