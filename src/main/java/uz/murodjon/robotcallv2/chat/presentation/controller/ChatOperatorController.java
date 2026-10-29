package uz.murodjon.robotcallv2.chat.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.OperatorChatMessageRequest;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * A person answering a text conversation in place of the agent (docs/api/chat.md): take it
 * over, write into it, hand it back. Each answers the conversation as {@code GET /api/chats/{id}} does.
 */
@RequestMapping("/api/chats")
public interface ChatOperatorController {

    @PreAuthorize("hasAuthority('CHAT_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/takeover")
    ResponseEntity<ResponseData<ChatDetail>> takeover(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CHAT_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/release")
    ResponseEntity<ResponseData<ChatDetail>> release(
            @CurrentCompanyId long companyId,
            @PathVariable long id);

    @PreAuthorize("hasAuthority('CHAT_AGENT_EDIT')")
    @PostMapping("/{id:\\d+}/messages")
    ResponseEntity<ResponseData<ChatDetail>> send(
            @CurrentCompanyId long companyId,
            @PathVariable long id,
            @Valid @RequestBody OperatorChatMessageRequest request);
}
