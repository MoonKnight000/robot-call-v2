package uz.murodjon.robotcallv2.chat.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartApiChatRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatApiUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ChatApiControllerImpl implements ChatApiController {

    private final ChatApiUseCase chatApiUseCase;

    public ChatApiControllerImpl(ChatApiUseCase chatApiUseCase) {
        this.chatApiUseCase = chatApiUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<StartChatResponse>> startChat(long companyId, StartApiChatRequest request) {
        return ResponseEntity.ok(ResponseData.ok(chatApiUseCase.startChat(companyId, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ChatReplyResponse>> sendMessage(long companyId, String sessionKey,
                                                                       SendChatMessageRequest request) {
        return ResponseEntity.ok(ResponseData.ok(chatApiUseCase.sendMessage(companyId, sessionKey, request)));
    }

    @Override
    public ResponseEntity<ResponseData<ChatReplyResponse>> endChat(long companyId, String sessionKey) {
        return ResponseEntity.ok(ResponseData.ok(chatApiUseCase.endChat(companyId, sessionKey)));
    }
}
