package uz.murodjon.robotcallv2.chat.application.port.input;

import uz.murodjon.robotcallv2.chat.application.dto.RegisterWebhookRequest;
import uz.murodjon.robotcallv2.chat.application.dto.SaveTelegramBotRequest;
import uz.murodjon.robotcallv2.chat.application.dto.TelegramBotRow;

import java.util.List;
import java.util.Map;

/** A company's Telegram bots, and the updates they receive (UNIVERSAL-OPERATOR-PLAN E.4). */
public interface TelegramUseCase {

    List<TelegramBotRow> findBots(long companyId);

    TelegramBotRow createBot(long companyId, SaveTelegramBotRequest request);

    /**
     * Tells Telegram to send this bot's updates to {@code publicBaseUrl}. The address is the
     * operator's to give: it is the one their deployment answers on, and this application has
     * no way of knowing it from the inside.
     */
    TelegramBotRow registerWebhook(long companyId, long botId, RegisterWebhookRequest request);

    /** Deletes the bot here and stops its updates at Telegram's end. */
    void deleteBot(long companyId, long botId);

    /**
     * One update from Telegram. Answers nothing: whatever the bot has to say goes back over
     * Telegram, not over this request.
     *
     * @param secretToken the {@code X-Telegram-Bot-Api-Secret-Token} header, which is what
     *                    makes the update provably Telegram's
     */
    void handleUpdate(long botId, String secretToken, Map<String, Object> update);
}
