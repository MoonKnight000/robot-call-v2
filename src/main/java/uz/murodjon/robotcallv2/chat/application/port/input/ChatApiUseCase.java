package uz.murodjon.robotcallv2.chat.application.port.input;

import uz.murodjon.robotcallv2.chat.application.dto.ChatReplyResponse;
import uz.murodjon.robotcallv2.chat.application.dto.SendChatMessageRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartApiChatRequest;
import uz.murodjon.robotcallv2.chat.application.dto.StartChatResponse;

/**
 * A chat agent inside the company's own app or site (Retell's create-chat / create-chat-completion):
 * the same conversation as the widget and Telegram, opened and fed by the company's server with an
 * API key instead of a visitor's browser.
 */
public interface ChatApiUseCase {

    StartChatResponse startChat(long companyId, StartApiChatRequest request);

    ChatReplyResponse sendMessage(long companyId, String sessionKey, SendChatMessageRequest request);

    /** Ends the conversation now; its post-chat analysis follows within a minute. */
    ChatReplyResponse endChat(long companyId, String sessionKey);
}
