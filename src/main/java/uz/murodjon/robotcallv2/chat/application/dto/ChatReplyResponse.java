package uz.murodjon.robotcallv2.chat.application.dto;

/**
 * The agent's answer to one message.
 *
 * @param ended         the agent ended the conversation — the widget closes the input rather
 *                      than letting a visitor type into a conversation nobody will answer
 * @param messagesLeft  how many messages this conversation may still hold
 */
public record ChatReplyResponse(String reply, boolean ended, int messagesLeft) {
}
