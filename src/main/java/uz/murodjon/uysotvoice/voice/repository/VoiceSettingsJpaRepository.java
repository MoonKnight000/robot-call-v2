package uz.murodjon.uysotvoice.voice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import uz.murodjon.uysotvoice.voice.entity.VoiceSettingsEntity;

import java.util.Optional;

/** Spring Data repository for {@link VoiceSettingsEntity}. */
@Repository
public interface VoiceSettingsJpaRepository extends JpaRepository<VoiceSettingsEntity, Long> {

    Optional<VoiceSettingsEntity> findByCompanyId(long companyId);
}
