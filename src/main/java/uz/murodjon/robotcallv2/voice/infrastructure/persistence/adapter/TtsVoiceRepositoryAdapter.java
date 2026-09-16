package uz.murodjon.robotcallv2.voice.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;

import uz.murodjon.robotcallv2.voice.infrastructure.persistence.mapper.TtsVoiceEntityMapper;
import uz.murodjon.robotcallv2.voice.application.port.output.TtsVoiceRepository;
import uz.murodjon.robotcallv2.voice.domain.entity.TtsVoice;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.entity.TtsVoiceEntity;
import uz.murodjon.robotcallv2.voice.infrastructure.persistence.repository.TtsVoiceJpaRepository;

import java.util.List;
import java.util.Locale;

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
        return jpaRepository.findAllByOrderByIdAsc().stream()
                .map(mapper::toTtsVoice)
                .toList();
    }

    @Override
    public List<TtsVoice> findByLanguage(String language) {
        if (isBlank(language)) {
            return findAll();
        }
        String wanted = languagePrefix(language);
        return jpaRepository.findAllByOrderByIdAsc().stream()
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
    public List<String> findIds() {
        return jpaRepository.findAllByOrderByIdAsc().stream()
                .map(TtsVoiceEntity::getId)
                .toList();
    }

    private static String languagePrefix(String language) {
        int dash = language.indexOf('-');
        return (dash > 0 ? language.substring(0, dash) : language).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
