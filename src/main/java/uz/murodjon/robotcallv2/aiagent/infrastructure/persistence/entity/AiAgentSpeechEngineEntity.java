package uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import uz.murodjon.robotcallv2.shared.dialog.PipelineMode;
import uz.murodjon.robotcallv2.shared.dialog.TranscriptionMode;

import java.util.List;

/** The ai_agent columns behind {@code AiAgentSpeechEngine}: which pipeline and vendors carry the call. */
@Embeddable
public class AiAgentSpeechEngineEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_mode", nullable = false)
    private PipelineMode pipelineMode = PipelineMode.CASCADE;

    @Column(name = "realtime_provider")
    private String realtimeProvider;

    @Column(name = "pipecat_stt")
    private String pipecatStt;

    @Column(name = "pipecat_llm")
    private String pipecatLlm;

    @Column(name = "pipecat_tts")
    private String pipecatTts;

    @Column(name = "stt_provider")
    private String sttProvider;

    @Column(name = "stt_model")
    private String sttModel;

    @Column(name = "tts_provider")
    private String ttsProvider;

    @Column(name = "tts_model")
    private String ttsModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "transcription_mode", length = 16)
    private TranscriptionMode transcriptionMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "boosted_keywords", columnDefinition = "jsonb")
    private List<String> boostedKeywords;

    protected AiAgentSpeechEngineEntity() {
    }

    public AiAgentSpeechEngineEntity(PipelineMode pipelineMode, String realtimeProvider,
                                     String pipecatStt, String pipecatLlm, String pipecatTts,
                                     String sttProvider, String sttModel, String ttsProvider, String ttsModel,
                                     TranscriptionMode transcriptionMode, List<String> boostedKeywords) {
        this.pipelineMode = pipelineMode != null ? pipelineMode : PipelineMode.CASCADE;
        this.realtimeProvider = realtimeProvider;
        this.pipecatStt = pipecatStt;
        this.pipecatLlm = pipecatLlm;
        this.pipecatTts = pipecatTts;
        this.sttProvider = sttProvider;
        this.sttModel = sttModel;
        this.ttsProvider = ttsProvider;
        this.ttsModel = ttsModel;
        this.transcriptionMode = transcriptionMode;
        this.boostedKeywords = boostedKeywords;
    }

    public PipelineMode getPipelineMode() {
        return pipelineMode;
    }

    public String getRealtimeProvider() {
        return realtimeProvider;
    }

    public String getPipecatStt() {
        return pipecatStt;
    }

    public String getPipecatLlm() {
        return pipecatLlm;
    }

    public String getPipecatTts() {
        return pipecatTts;
    }

    public String getSttProvider() {
        return sttProvider;
    }

    public String getSttModel() {
        return sttModel;
    }

    public String getTtsProvider() {
        return ttsProvider;
    }

    public String getTtsModel() {
        return ttsModel;
    }

    public TranscriptionMode getTranscriptionMode() {
        return transcriptionMode;
    }

    public List<String> getBoostedKeywords() {
        return boostedKeywords;
    }
}
