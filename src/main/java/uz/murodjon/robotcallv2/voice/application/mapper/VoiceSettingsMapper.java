package uz.murodjon.robotcallv2.voice.application.mapper;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.entity.CompanyEntity;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.VoiceSettingsEntity;

@Component
public class VoiceSettingsMapper {

    public VoiceSettings entityToDomain(VoiceSettingsEntity entity) {
        if (entity == null) {
            return null;
        }
        return new VoiceSettings(
                entity.getCompanyId(),
                entity.getSpeed(),
                entity.getPitch(),
                entity.getCreatedAt()
        );
    }

    public VoiceSettingsEntity domainToEntity(VoiceSettings domain, CompanyEntity company) {
        if (domain == null) {
            return null;
        }
        VoiceSettingsEntity entity = new VoiceSettingsEntity();
        entity.setCompany(company);
        entity.setSpeed(domain.speed());
        entity.setPitch(domain.pitch());
        entity.setCreatedAt(domain.createdAt());
        return entity;
    }
}
