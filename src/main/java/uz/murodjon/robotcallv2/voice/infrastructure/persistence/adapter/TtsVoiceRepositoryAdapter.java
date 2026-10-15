package uz.murodjon.robotcallv2.voice.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.mapper.TtsVoiceEntityMapper;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository.TtsVoiceJpaRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TtsVoiceRepositoryAdapter implements TtsVoiceRepository {

    private final TtsVoiceJpaRepository jpaRepository;
    private final TtsVoiceEntityMapper mapper;

    public TtsVoiceRepositoryAdapter(TtsVoiceJpaRepository jpaRepository, TtsVoiceEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public List<TtsVoice> findAll() {
        return jpaRepository.findAllByActiveTrueOrderByIdAsc().stream()
                .map(mapper::toTtsVoice)
                .toList();
    }

    @Override
    public List<TtsVoice> findByLanguage(String language) {
        if (isBlank(language)) {
            return findAll();
        }
        String wanted = languagePrefix(language);
        return jpaRepository.findAllByActiveTrueOrderByIdAsc().stream()
                .filter(v -> v.getLanguage() != null && languagePrefix(v.getLanguage()).equals(wanted))
                .map(mapper::toTtsVoice)
                .toList();
    }

    @Override
    public TtsVoice find(String id) {
        if (isBlank(id)) {
            return null;
        }
        return jpaRepository.findById(id.trim().toLowerCase(Locale.ROOT))
                .map(mapper::toTtsVoice)
                .orElse(null);
    }

    @Override
    public TtsVoice findActiveById(String id) {
        if (isBlank(id)) {
            return null;
        }
        return jpaRepository.findById(id.trim().toLowerCase(Locale.ROOT))
                .filter(TtsVoiceEntity::isActive)
                .map(mapper::toTtsVoice)
                .orElse(null);
    }

    @Override
    public List<String> findIds() {
        return jpaRepository.findAllByOrderByIdAsc().stream()
                .map(TtsVoiceEntity::getId)
                .toList();
    }

    @Override
    public long countActiveByCompanyId(long companyId) {
        return jpaRepository.countByCompanyIdAndActiveTrue(companyId);
    }

    @Override
    public void create(TtsVoice voice) {
        TtsVoiceEntity entity = mapper.toTtsVoiceEntity(voice);
        entity.setActive(true);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void deactivate(String id) {
        jpaRepository.findById(id).ifPresent(entity -> entity.setActive(false));
    }

    @Override
    @Transactional
    public void replaceSyncedVoices(String provider, List<TtsVoice> voices, Instant syncedAt) {
        Set<String> listed = voices.stream().map(TtsVoice::id).collect(Collectors.toSet());
        List<TtsVoiceEntity> changed = new ArrayList<>();
        for (TtsVoiceEntity previous : jpaRepository.findAllByProviderAndSyncedAtIsNotNull(provider)) {
            if (!listed.contains(previous.getId()) && previous.isActive()) {
                previous.setActive(false);
                changed.add(previous);
            }
        }
        for (TtsVoice voice : voices) {
            TtsVoiceEntity entity = mapper.toTtsVoiceEntity(voice);
            entity.setActive(true);
            entity.setSyncedAt(syncedAt);
            changed.add(entity);
        }
        jpaRepository.saveAll(changed);
    }

    private static String languagePrefix(String language) {
        int dash = language.indexOf('-');
        return (dash > 0 ? language.substring(0, dash) : language).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
