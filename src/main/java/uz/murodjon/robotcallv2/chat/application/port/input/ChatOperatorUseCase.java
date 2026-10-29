package uz.murodjon.robotcallv2.chat.application.port.input;

import uz.murodjon.robotcallv2.chat.application.dto.ChatDetail;
import uz.murodjon.robotcallv2.chat.application.dto.OperatorChatMessageRequest;

/**
 * A person from the company answering a text conversation in place of the agent: take it over,
 * write into it, hand it back. Every method answers the conversation as the panel shows it.
 */
public interface ChatOperatorUseCase {

    /** The agent stops answering; the signed-in user does. {@code 409 CHAT_SESSION_ENDED} once it is over. */
    ChatDetail takeoverChat(long companyId, long chatId);

    /** The agent answers again, carrying on from what the operator said. */
    ChatDetail releaseChat(long companyId, long chatId);

    /**
     * A line from the operator: stored, and sent over Telegram for a bot conversation (a widget
     * picks it up on its next poll). {@code 409 CHAT_NOT_TAKEN_OVER} while the agent answers.
     */
    ChatDetail sendOperatorMessage(long companyId, long chatId, OperatorChatMessageRequest request);
}
