package uz.murodjon.robotcallv2.callrecord.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * JPA entity for {@code call_technical} (§10.5 "Texnik" tab).
 */
@Entity
@Table(name = "call_technical")
public class CallTechnicalEntity {

    @Id
    @Column(name = "call_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "call_id")
    private CallAttemptEntity call;

    @Column(name = "channel_name")
    private String channelName;

    @Column(name = "trunk")
    private String trunk;

    @Column(name = "amd_result")
    private String amdResult;

    @Column(name = "stt_provider")
    private String sttProvider;

    @Column(name = "tts_provider")
    private String ttsProvider;

    @Column(name = "tts_voice")
    private String ttsVoice;

    @Column(name = "llm_model")
    private String llmModel;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "cached_tokens")
    private Integer cachedTokens;

    @Column(name = "turn_count")
    private Integer turnCount;

    @Column(name = "avg_turn_latency_ms")
    private Integer avgTurnLatencyMs;

    @Column(name = "max_turn_latency_ms")
    private Integer maxTurnLatencyMs;

    @Column(name = "avg_llm_latency_ms")
    private Integer avgLlmLatencyMs;

    @Column(name = "max_llm_latency_ms")
    private Integer maxLlmLatencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CallAttemptEntity getCall() {
        return call;
    }

    public void setCall(CallAttemptEntity call) {
        this.call = call;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getTrunk() {
        return trunk;
    }

    public void setTrunk(String trunk) {
        this.trunk = trunk;
    }

    public String getAmdResult() {
        return amdResult;
    }

    public void setAmdResult(String amdResult) {
        this.amdResult = amdResult;
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

    public String getTtsVoice() {
        return ttsVoice;
    }

    public void setTtsVoice(String ttsVoice) {
        this.ttsVoice = ttsVoice;
    }

    public String getLlmModel() {
        return llmModel;
    }

    public void setLlmModel(String llmModel) {
        this.llmModel = llmModel;
    }

    public Integer getPromptTokens() {
        return promptTokens;
    }

    public void setPromptTokens(Integer promptTokens) {
        this.promptTokens = promptTokens;
    }

    public Integer getCompletionTokens() {
        return completionTokens;
    }

    public void setCompletionTokens(Integer completionTokens) {
        this.completionTokens = completionTokens;
    }

    public Integer getCachedTokens() {
        return cachedTokens;
    }

    public void setCachedTokens(Integer cachedTokens) {
        this.cachedTokens = cachedTokens;
    }

    public Integer getTurnCount() {
        return turnCount;
    }

    public void setTurnCount(Integer turnCount) {
        this.turnCount = turnCount;
    }

    public Integer getAvgTurnLatencyMs() {
        return avgTurnLatencyMs;
    }

    public void setAvgTurnLatencyMs(Integer avgTurnLatencyMs) {
        this.avgTurnLatencyMs = avgTurnLatencyMs;
    }

    public Integer getMaxTurnLatencyMs() {
        return maxTurnLatencyMs;
    }

    public void setMaxTurnLatencyMs(Integer maxTurnLatencyMs) {
        this.maxTurnLatencyMs = maxTurnLatencyMs;
    }

    public Integer getAvgLlmLatencyMs() {
        return avgLlmLatencyMs;
    }

    public void setAvgLlmLatencyMs(Integer avgLlmLatencyMs) {
        this.avgLlmLatencyMs = avgLlmLatencyMs;
    }

    public Integer getMaxLlmLatencyMs() {
        return maxLlmLatencyMs;
    }

    public void setMaxLlmLatencyMs(Integer maxLlmLatencyMs) {
        this.maxLlmLatencyMs = maxLlmLatencyMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
