package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity.VoicemailMessageEntity;

import java.util.Optional;

@Repository
public interface VoicemailMessageJpaRepository extends JpaRepository<VoicemailMessageEntity, Long>,
        JpaSpecificationExecutor<VoicemailMessageEntity> {

    Optional<VoicemailMessageEntity> findByIdAndCompanyId(long id, long companyId);
}
