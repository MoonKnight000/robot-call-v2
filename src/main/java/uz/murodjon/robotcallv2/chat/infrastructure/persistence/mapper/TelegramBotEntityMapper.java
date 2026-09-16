package uz.murodjon.robotcallv2.chat.infrastructure.persistence.mapper;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.chat.domain.entity.TelegramBot;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.TelegramBotEntity;

import java.time.Instant;

@Component
public class TelegramBotEntityMapper {

    public TelegramBot toTelegramBot(TelegramBotEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TelegramBot(entity.getId(), entity.getCompanyId(), entity.getAiAgentId(), entity.getName(),
                entity.getTokenSecretKey(), entity.getWebhookSecret(), entity.getLanguage(),
                entity.getWebhookUrl(), entity.isEnabled(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    public TelegramBotEntity toTelegramBotEntity(TelegramBot bot, TelegramBotEntity entity) {
        Instant now = Instant.now();
        entity.setId(bot.id());
        entity.setCompanyId(bot.companyId());
        entity.setAiAgentId(bot.aiAgentId());
        entity.setName(bot.name());
        entity.setTokenSecretKey(bot.tokenSecretKey());
        entity.setWebhookSecret(bot.webhookSecret());
        entity.setLanguage(bot.language());
        entity.setWebhookUrl(bot.webhookUrl());
        entity.setEnabled(bot.enabled());
        entity.setCreatedAt(bot.createdAt() != null ? bot.createdAt() : now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
