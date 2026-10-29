package uz.murodjon.robotcallv2.chat.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.OperatorChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatOperatorUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ChatOperatorControllerImpl implements ChatOperatorController {

    private final ChatOperatorUseCase chatOperatorUseCase;

    public ChatOperatorControllerImpl(ChatOperatorUseCase chatOperatorUseCase) {
        this.chatOperatorUseCase = chatOperatorUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<ChatDetail>> takeover(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(chatOperatorUseCase.takeoverChat(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<ChatDetail>> release(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(chatOperatorUseCase.releaseChat(companyId, id)));
    }

    @Override
    public ResponseEntity<ResponseData<ChatDetail>> send(long companyId, long id, OperatorChatMessageRequest request) {
        return ResponseEntity.ok(ResponseData.ok(chatOperatorUseCase.sendOperatorMessage(companyId, id, request)));
    }
}
