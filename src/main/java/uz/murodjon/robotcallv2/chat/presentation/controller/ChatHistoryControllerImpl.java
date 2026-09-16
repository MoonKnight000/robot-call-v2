package uz.murodjon.robotcallv2.chat.presentation.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.ChatRow;
import uz.murodjon.robotcallv2.chat.application.port.input.ChatHistoryUseCase;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

@RestController
public class ChatHistoryControllerImpl implements ChatHistoryController {

    private final ChatHistoryUseCase chatHistoryUseCase;

    public ChatHistoryControllerImpl(ChatHistoryUseCase chatHistoryUseCase) {
        this.chatHistoryUseCase = chatHistoryUseCase;
    }

    @Override
    public ResponseEntity<ResponseData<PageableData<ChatRow>>> list(long companyId, ChatFilter filter) {
        return ResponseEntity.ok(ResponseData.ok(chatHistoryUseCase.findChats(companyId, filter)));
    }

    @Override
    public ResponseEntity<ResponseData<ChatDetail>> get(long companyId, long id) {
        return ResponseEntity.ok(ResponseData.ok(chatHistoryUseCase.requireChat(companyId, id)));
    }
}
