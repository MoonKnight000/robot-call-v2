package uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.voicemail.application.port.output.VoicemailBoxRepository;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBox;
import uz.murodjon.robotcallv2.voicemail.domain.entity.VoicemailBoxFilter;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.entity.VoicemailBoxEntity;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.mapper.VoicemailEntityMapper;
import uz.murodjon.robotcallv2.voicemail.infrastructure.persistence.repository.VoicemailBoxJpaRepository;

import java.time.Instant;
import java.util.List;

@Component
public class VoicemailBoxRepositoryAdapter implements VoicemailBoxRepository {

    private final VoicemailBoxJpaRepository jpaRepository;
    private final VoicemailEntityMapper mapper;

    public VoicemailBoxRepositoryAdapter(VoicemailBoxJpaRepository jpaRepository, VoicemailEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public long create(long companyId, VoicemailBox box) {
        VoicemailBoxEntity entity = new VoicemailBoxEntity();
        entity.setCompanyId(companyId);
        entity.setCreatedAt(Instant.now());
        copy(box, entity);
        return jpaRepository.save(entity).getId();
    }

    @Override
    public void update(long companyId, long id, VoicemailBox box) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(entity -> {
            copy(box, entity);
            jpaRepository.save(entity);
        });
    }

    @Override
    public void delete(long companyId, long id) {
        jpaRepository.findByIdAndCompanyId(id, companyId).ifPresent(jpaRepository::delete);
    }

    @Override
    public VoicemailBox find(long companyId, long id) {
        return jpaRepository.findByIdAndCompanyId(id, companyId).map(mapper::toVoicemailBox).orElse(null);
    }

    @Override
    public List<VoicemailBox> findAll(long companyId, VoicemailBoxFilter filter) {
        return jpaRepository.findByCompanyId(companyId, filter.pageable()).stream().map(mapper::toVoicemailBox).toList();
    }

    @Override
    public long count(long companyId) {
        return jpaRepository.countByCompanyId(companyId);
    }

    private static void copy(VoicemailBox box, VoicemailBoxEntity entity) {
        entity.setName(box.name());
        entity.setGreetingText(box.greetingText());
        entity.setGreetingFileId(box.greetingFileId());
        entity.setMaxSeconds(box.maxSeconds());
        entity.setTranscribe(box.transcribe());
        entity.setEnabled(box.enabled());
    }
}
