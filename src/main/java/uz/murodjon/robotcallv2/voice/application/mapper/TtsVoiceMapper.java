package uz.murodjon.robotcallv2.voice.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;

@Component
public class TtsVoiceMapper {

    public TtsVoice entityToDomain(TtsVoiceEntity entity) {
        if (entity == null) {
            return null;
        }
        return new TtsVoice(
                entity.getId(),
                entity.getProvider(),
                entity.getLanguage(),
                entity.getName(),
                entity.getLabel(),
                entity.getRole()
        );
    }

    public TtsVoiceEntity domainToEntity(TtsVoice domain) {
        if (domain == null) {
            return null;
        }
        TtsVoiceEntity entity = new TtsVoiceEntity();
        entity.setId(domain.id());
        entity.setProvider(domain.provider());
        entity.setLanguage(domain.language());
        entity.setName(domain.name());
        entity.setLabel(domain.label());
        entity.setRole(domain.role());
        return entity;
    }
}
