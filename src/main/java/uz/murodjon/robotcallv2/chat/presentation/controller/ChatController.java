package uz.murodjon.robotcallv2.chat.presentation.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;
import uz.murodjon.robotcallv2.shared.api.ResponseData;

/**
 * The widget's text channel ({@code /api/public/widgets/{key}/chat},
 * UNIVERSAL-OPERATOR-PLAN E.4): the same agent the button on that page would have called,
 * answering in writing instead.
 *
 * <p>Public and token-less, like the widget's other two endpoints, and guarded the same way:
 * the calling page's origin has to be one the widget lists, and the agent's day has to have
 * room. There is no panel-side endpoint here on purpose — a conversation is opened by a
 * visitor or not at all.
 */
@RequestMapping("/api/public/widgets")
public interface ChatController {

    /**
     * Opens a conversation and answers with the agent's opening line. The {@code sessionKey}
     * that comes back is what every following message carries; the id is never exposed.
     */
    @PostMapping("/{widgetKey}/chat")
    ResponseEntity<ResponseData<StartChatResponse>> startChat(
            @PathVariable String widgetKey,
            @RequestParam(required = false) String language,
            @RequestHeader(value = "Origin", required = false) String origin);

    /**
     * Answers one message. A conversation that has ended, run out of messages or gone quiet
     * for too long is refused rather than reopened — the visitor starts a new one.
     */
    @PostMapping("/{widgetKey}/chat/{sessionKey}")
    ResponseEntity<ResponseData<ChatReplyResponse>> sendMessage(
            @PathVariable String widgetKey,
            @PathVariable String sessionKey,
            @RequestHeader(value = "Origin", required = false) String origin,
            @Valid @RequestBody SendChatMessageRequest request);
}
