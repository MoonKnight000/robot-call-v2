package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity.VoicemailBoxEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoicemailBoxJpaRepository extends JpaRepository<VoicemailBoxEntity, Long> {

    Optional<VoicemailBoxEntity> findByIdAndCompanyId(long id, long companyId);

    List<VoicemailBoxEntity> findByCompanyId(long companyId, Pageable pageable);

    long countByCompanyId(long companyId);
}
