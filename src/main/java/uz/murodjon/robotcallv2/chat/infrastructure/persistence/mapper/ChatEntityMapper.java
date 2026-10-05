package uz.murodjon.robotcallv2.chat.infrastructure.persistence.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatMessage;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatSession;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.ChatMessageEntity;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.ChatSessionEntity;

import java.time.Instant;
import java.util.Map;

/** Maps the chat rows to their domain records; the outcome is a JSON column. */
@Component
public class ChatEntityMapper {

    private final ObjectMapper objectMapper;
    private static final TypeReference<Map<String, Object>> OUTCOME_TYPE = new TypeReference<>() {};

    public ChatEntityMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ChatSession toChatSession(ChatSessionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ChatSession(entity.getId(), entity.getSessionKey(), entity.getCompanyId(),
                entity.getAiAgentId(), entity.getWidgetId(), entity.getTelegramBotId(),
                entity.getTelegramChatId(), entity.getLanguage(), entity.getStage(),
                readOutcome(entity.getOutcome()), entity.getDisposition(), entity.isEnded(),
                entity.getMessageCount(), entity.getCreatedAt(), entity.getLastMessageAt());
    }

    public ChatSessionEntity toChatSessionEntity(ChatSession session, ChatSessionEntity entity) {
        Instant now = Instant.now();
        entity.setId(session.id());
        entity.setSessionKey(session.sessionKey());
        entity.setCompanyId(session.companyId());
        entity.setAiAgentId(session.aiAgentId());
        entity.setWidgetId(session.widgetId());
        entity.setTelegramBotId(session.telegramBotId());
        entity.setTelegramChatId(session.telegramChatId());
        entity.setLanguage(session.language());
        entity.setStage(session.stage());
        entity.setOutcome(writeOutcome(session.outcome()));
        entity.setDisposition(session.disposition());
        entity.setEnded(session.ended());
        entity.setMessageCount(session.messageCount());
        entity.setCreatedAt(session.createdAt() != null ? session.createdAt() : now);
        entity.setLastMessageAt(session.lastMessageAt() != null ? session.lastMessageAt() : now);
        return entity;
    }

    public ChatMessage toChatMessage(ChatMessageEntity entity) {
        if (entity == null) {
            return null;
        }
        return new ChatMessage(entity.getId(), entity.getChatSessionId(), entity.getRole(), entity.getText(),
                entity.getCreatedAt());
    }

    public ChatMessageEntity toChatMessageEntity(ChatMessage message) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setChatSessionId(message.chatSessionId());
        entity.setRole(message.role());
        entity.setText(message.text());
        entity.setCreatedAt(message.createdAt() != null ? message.createdAt() : Instant.now());
        return entity;
    }

    private Map<String, Object> readOutcome(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, OUTCOME_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String writeOutcome(Map<String, Object> outcome) {
        try {
            return objectMapper.writeValueAsString(outcome);
        } catch (Exception e) {
            // A scenario tool records plain values; anything else is a defect, not a chat.
            throw new IllegalStateException("Chat outcome could not be serialised", e);
        }
    }
}
