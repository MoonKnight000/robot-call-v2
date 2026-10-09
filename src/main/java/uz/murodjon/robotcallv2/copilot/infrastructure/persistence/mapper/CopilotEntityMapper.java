package uz.murodjon.robotcallv2.copilot.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotMessage;
import uz.murodjon.robotcallv2.copilot.domain.entity.CopilotSession;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity.CopilotMessageEntity;
import uz.murodjon.robotcallv2.copilot.infrastructure.persistence.entity.CopilotSessionEntity;

@Component
public class CopilotEntityMapper {

    public CopilotSession toCopilotSession(CopilotSessionEntity entity) {
        return new CopilotSession(entity.getId(), entity.getCompanyId(), entity.getUserId(), entity.getTitle(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public CopilotMessage toCopilotMessage(CopilotMessageEntity entity) {
        return new CopilotMessage(entity.getId(), entity.getSessionId(), entity.getRole(), entity.getContent(),
                entity.getToolName(), entity.getToolArguments(), entity.isToolError(), entity.getPromptTokens(),
                entity.getCompletionTokens(), entity.getChargedUzs(), entity.getCreatedAt());
    }

    public CopilotMessageEntity toCopilotMessageEntity(long companyId, CopilotMessage message) {
        CopilotMessageEntity entity = new CopilotMessageEntity();
        entity.setCompanyId(companyId);
        entity.setSessionId(message.sessionId());
        entity.setRole(message.role());
        entity.setContent(message.content());
        entity.setToolName(message.toolName());
        entity.setToolArguments(message.toolArguments());
        entity.setToolError(message.toolError());
        entity.setPromptTokens(message.promptTokens());
        entity.setCompletionTokens(message.completionTokens());
        return entity;
    }
}
