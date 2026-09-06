package uz.murodjon.robotcallv2.aimodel.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import uz.murodjon.robotcallv2.engine.domain.enums.PipelineMode;

@Entity
@Table(name = "ai_model")
public class AiModelEntity {

    @Id
    private String id;

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
