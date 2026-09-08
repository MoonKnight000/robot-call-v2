package uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import uz.murodjon.robotcallv2.aiagent.infrastructure.persistence.entity.AiAgentEntity;

import java.time.Instant;

@Entity
@Table(name = "ai_agent_tool")
public class AiAgentToolEntity {

    @EmbeddedId
    private AiAgentToolId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("aiAgentId")
    @JoinColumn(name = "ai_agent_id")
    private AiAgentEntity aiAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("toolId")
    @JoinColumn(name = "tool_id")
    private ToolEntity tool;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AiAgentToolEntity() {
    }

    /** {@code @MapsId}: the two sides are the key, so the embedded id fills itself in. */
    public AiAgentToolEntity(AiAgentEntity aiAgent, ToolEntity tool) {
        this.id = new AiAgentToolId();
        this.aiAgent = aiAgent;
        this.tool = tool;
        this.createdAt = Instant.now();
    }

    public AiAgentToolId getId() {
        return id;
    }

    public AiAgentEntity getAiAgent() {
        return aiAgent;
    }

    public void setAiAgent(AiAgentEntity aiAgent) {
        this.aiAgent = aiAgent;
    }

    public ToolEntity getTool() {
        return tool;
    }

    public void setTool(ToolEntity tool) {
        this.tool = tool;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
