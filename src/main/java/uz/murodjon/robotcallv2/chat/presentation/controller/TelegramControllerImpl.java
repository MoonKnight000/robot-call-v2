package uz.murodjon.robotcallv2.chat.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.chat.application.dto.RegisterWebhookRequest;
import uz.murodjon.robotcallv2.chat.application.dto.SaveTelegramBotRequest;
import uz.murodjon.robotcallv2.chat.application.dto.TelegramBotRow;
import uz.murodjon.robotcallv2.chat.application.port.input.TelegramUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

import java.util.List;
import java.util.Map;

@RestController
public class TelegramControllerImpl implements TelegramController {

    private final TelegramUseCase telegramUseCase;

    public TelegramControllerImpl(TelegramUseCase telegramUseCase) {
        this.telegramUseCase = telegramUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<List<TelegramBotRow>>> list(long companyId) {
        return ResponseEntity.ok(ResponseData.ok(telegramUseCase.findBots(companyId)));
    }

    @Override
    public ResponseEntity<ResponseData<TelegramBotRow>> create(long companyId, SaveTelegramBotRequest request) {
        return ResponseEntity.ok(ResponseData.ok(telegramUseCase.createBot(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<TelegramBotRow>> registerWebhook(long companyId, long botId,
                                                                       RegisterWebhookRequest request) {
        return ResponseEntity.ok(ResponseData.ok(telegramUseCase.registerWebhook(companyId, botId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<Void>> delete(long companyId, long botId) {
        telegramUseCase.deleteBot(companyId, botId);
        return ResponseEntity.ok(ResponseData.ok(null));
    }

    @Override
    public ResponseEntity<Void> receiveUpdate(long botId, String secretToken, Map<String, Object> update) {
        telegramUseCase.handleUpdate(botId, secretToken, update);
        return ResponseEntity.ok().build();
    }
}
