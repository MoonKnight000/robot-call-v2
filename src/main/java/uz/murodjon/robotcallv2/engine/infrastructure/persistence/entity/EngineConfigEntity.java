package uz.murodjon.robotcallv2.engine.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

import java.time.Instant;

/**
 * JPA entity for engine_config (§11 settings).
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

    public void setId(Long id) {
        this.id = id;
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
