package uz.murodjon.robotcallv2.chat.infrastructure.persistence.adapter;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.murodjon.robotcallv2.chat.application.port.output.TelegramBotRepository;
import uz.murodjon.robotcallv2.chat.domain.entity.TelegramBot;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.entity.TelegramBotEntity;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.mapper.TelegramBotEntityMapper;
import uz.murodjon.robotcallv2.chat.infrastructure.persistence.repository.TelegramBotJpaRepository;

import java.util.List;

@Component
public class TelegramBotRepositoryAdapter implements TelegramBotRepository {

    private final TelegramBotJpaRepository jpaRepository;
    private final TelegramBotEntityMapper mapper;

    public TelegramBotRepositoryAdapter(TelegramBotJpaRepository jpaRepository, TelegramBotEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public TelegramBot save(TelegramBot bot) {
        TelegramBotEntity entity = bot.id() == null
                ? new TelegramBotEntity()
                : jpaRepository.findById(bot.id()).orElseGet(TelegramBotEntity::new);
        return mapper.toTelegramBot(jpaRepository.save(mapper.toTelegramBotEntity(bot, entity)));
    }

    @Override
    public TelegramBot findById(long id) {
        return jpaRepository.findById(id).map(mapper::toTelegramBot).orElse(null);
    }

    @Override
    public TelegramBot findByCompanyIdAndId(long companyId, long id) {
        return jpaRepository.findByCompanyIdAndId(companyId, id).map(mapper::toTelegramBot).orElse(null);
    }

    @Override
    public List<TelegramBot> findByCompanyId(long companyId) {
        return jpaRepository.findByCompanyIdOrderByIdAsc(companyId).stream().map(mapper::toTelegramBot).toList();
    }

    @Override
    @Transactional
    public void deleteByCompanyIdAndId(long companyId, long id) {
        jpaRepository.deleteByCompanyIdAndId(companyId, id);
    }
}
