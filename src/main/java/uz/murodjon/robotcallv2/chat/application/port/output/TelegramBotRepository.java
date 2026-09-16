package uz.murodjon.robotcallv2.chat.application.port.output;

import uz.murodjon.robotcallv2.chat.domain.entity.TelegramBot;

import java.util.List;

public interface TelegramBotRepository {

    TelegramBot save(TelegramBot bot);

    /** By id alone: an incoming update carries no company, and the secret is what proves it. */
    TelegramBot findById(long id);

    TelegramBot findByCompanyIdAndId(long companyId, long id);

    List<TelegramBot> findByCompanyId(long companyId);

    void deleteByCompanyIdAndId(long companyId, long id);
}
