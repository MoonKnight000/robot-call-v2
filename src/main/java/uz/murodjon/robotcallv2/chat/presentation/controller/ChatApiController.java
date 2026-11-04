package uz.murodjon.robotcallv2.chat.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartApiChatRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.security.CurrentCompanyId;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The chat agent inside the company's own app or site (docs/api/chat-api.md): its server opens a
 * conversation, passes each message of the person and gets the agent's reply. Meant for an API
 * key with {@code CHAT_AGENT_EDIT}; the conversations show up in the panel like any other chat.
 */
@RequestMapping("/api/v1/chats")
public interface ChatApiController {

    /** Opens a conversation with a CHAT or OMNICHANNEL agent; answers with its opening line. */
    @PreAuthorize("hasAuthority('CHAT_AGENT_EDIT')")
    @PostMapping
    ResponseEntity<ResponseData<StartChatResponse>> startChat(
            @CurrentCompanyId long companyId,
            @Valid @RequestBody StartApiChatRequest request);

    /** One message of the person; answers with the agent's reply. */
    @PreAuthorize("hasAuthority('CHAT_AGENT_EDIT')")
    @PostMapping("/{sessionKey}/messages")
    ResponseEntity<ResponseData<ChatReplyResponse>> sendMessage(
            @CurrentCompanyId long companyId,
            @PathVariable String sessionKey,
            @Valid @RequestBody SendChatMessageRequest request);

    /** Ends the conversation; the post-chat analysis and CHAT_ENDED webhook follow. */
    @PreAuthorize("hasAuthority('CHAT_AGENT_EDIT')")
    @PostMapping("/{sessionKey}/end")
    ResponseEntity<ResponseData<ChatReplyResponse>> endChat(
            @CurrentCompanyId long companyId,
            @PathVariable String sessionKey);
}
