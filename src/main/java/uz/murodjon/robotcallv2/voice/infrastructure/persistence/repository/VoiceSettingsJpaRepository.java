package uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.VoiceSettingsEntity;

import java.util.Optional;

@Repository
public interface VoiceSettingsJpaRepository extends JpaRepository<VoiceSettingsEntity, Long> {

    Optional<VoiceSettingsEntity> findByCompanyId(long companyId);
}
