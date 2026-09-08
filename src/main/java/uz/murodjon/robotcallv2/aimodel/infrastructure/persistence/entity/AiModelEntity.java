package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity;

import jakarta.persistence.*;
import uz.murodjon.robotcallv2.aiagent.domain.enums.PipelineMode;
import uz.murodjon.robotcallv2.aimodel.domain.enums.AiModelKind;

@Entity
@Table(name = "ai_model")
public class AiModelEntity {

    @Id
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AiModelKind kind = AiModelKind.LLM;

    @Column(nullable = false)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineMode mode;

    @Column(nullable = false)
    private String label;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AiModelKind getKind() {
        return kind;
    }

    public void setKind(AiModelKind kind) {
        this.kind = kind;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public PipelineMode getMode() {
        return mode;
    }

    public void setMode(PipelineMode mode) {
        this.mode = mode;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
