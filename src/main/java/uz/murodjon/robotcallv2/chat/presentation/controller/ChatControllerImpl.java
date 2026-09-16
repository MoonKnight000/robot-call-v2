package uz.murodjon.robotcallv2.chat.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatUseCase;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ChatControllerImpl implements ChatController {

    private final ChatUseCase chatUseCase;

    public ChatControllerImpl(ChatUseCase chatUseCase) {
        this.chatUseCase = chatUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<StartChatResponse>> startChat(String widgetKey, String language,
                                                                    String origin) {
        return ResponseEntity.ok(ResponseData.ok(chatUseCase.startChat(widgetKey, origin, language)));
    }

    @Override
    public ResponseEntity<ResponseData<ChatReplyResponse>> sendMessage(String widgetKey, String sessionKey,
                                                                      String origin,
                                                                      SendChatMessageRequest request) {
        return ResponseEntity.ok(ResponseData.ok(chatUseCase.sendMessage(widgetKey, origin, sessionKey, request)));
    }
}
