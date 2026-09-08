package uz.murodjon.robotcallv2.tool.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class AiAgentToolId implements Serializable {

    @Column(name = "ai_agent_id", nullable = false)
    private Long aiAgentId;

    @Column(name = "tool_id", nullable = false)
    private Long toolId;

    public AiAgentToolId() {
    }

    public AiAgentToolId(Long aiAgentId, Long toolId) {
        this.aiAgentId = aiAgentId;
        this.toolId = toolId;
    }

    public Long getAiAgentId() {
        return aiAgentId;
    }

    public void setAiAgentId(Long aiAgentId) {
        this.aiAgentId = aiAgentId;
    }

    public Long getToolId() {
        return toolId;
    }

    public void setToolId(Long toolId) {
        this.toolId = toolId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiAgentToolId that = (AiAgentToolId) o;
        return Objects.equals(aiAgentId, that.aiAgentId) && Objects.equals(toolId, that.toolId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aiAgentId, toolId);
    }
}
