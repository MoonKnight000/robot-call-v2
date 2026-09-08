package uz.murodjon.robotcallv2.voice.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.company.infrastructure.persistence.repository.CompanyJpaRepository;
import uz.murodjon.robotcallv2.voice.application.mapper.VoiceSettingsMapper;
import uz.murodjon.robotcallv2.voice.application.port.output.VoiceSettingsRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.VoiceSettings;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.VoiceSettingsEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository.VoiceSettingsJpaRepository;

import java.time.Instant;

@Component
public class VoiceSettingsRepositoryAdapter implements VoiceSettingsRepository {

    private final VoiceSettingsJpaRepository jpaRepository;
    private final VoiceSettingsMapper mapper;
    private final CompanyJpaRepository companyJpaRepository;

    public VoiceSettingsRepositoryAdapter(VoiceSettingsJpaRepository jpaRepository, VoiceSettingsMapper mapper,
                                          CompanyJpaRepository companyJpaRepository) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
        this.companyJpaRepository = companyJpaRepository;
    }

    @Override
    public VoiceSettings findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyId(companyId).map(mapper::entityToDomain).orElse(null);
    }

    @Override
    public VoiceSettings upsert(long companyId, Double speed, Double pitch) {
        VoiceSettingsEntity entity = jpaRepository.findByCompanyId(companyId).orElseGet(() -> {
            VoiceSettingsEntity fresh = new VoiceSettingsEntity();
            fresh.setCompany(companyJpaRepository.getReferenceById(companyId));
            fresh.setCreatedAt(Instant.now());
            return fresh;
        });
        entity.setSpeed(speed);
        entity.setPitch(pitch);
        return mapper.entityToDomain(jpaRepository.save(entity));
    }
}
