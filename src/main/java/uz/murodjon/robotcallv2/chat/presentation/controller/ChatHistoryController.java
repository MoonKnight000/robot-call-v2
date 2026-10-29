package uz.murodjon.robotcallv2.chat.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.ChatRow;
import uz.murodjon.robotcallv2.chat.domain.entity.ChatFilter;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.PageableData;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The company's widget and Telegram conversations as the panel reads them
 * ({@code /api/chats}, docs/api/chat.md). Read-only, and under the same right as the call
 * list and its transcripts: a chat transcript is the same kind of data.
 */
@RequestMapping("/api/chats")
public interface ChatHistoryController {

    @PreAuthorize("hasAuthority('CHAT_AGENT_READ')")
    @PostMapping("/list")
    ResponseEntity<ResponseData<PageableData<ChatRow>>> list(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody ChatFilter filter);

    /** One conversation with what its tools recorded and the whole transcript. */
    @PreAuthorize("hasAuthority('CHAT_AGENT_READ')")
    @GetMapping("/{id:\\d+}")
    ResponseEntity<ResponseData<ChatDetail>> get(
            @CurrentCompanyId long companyId,
            @PathVariable long id);
}
