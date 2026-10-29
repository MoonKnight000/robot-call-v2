package uz.murodjon.robotcallv2.chat.application.dto;

/**
 * The agent's answer to one message.
 *
 * @param reply          the agent's line; {@code null} while a person from the company answers
 *                       instead ({@code operatorActive}) — their lines arrive through
 *                       {@code GET …/messages}
 * @param ended          the agent ended the conversation — the widget closes the input rather
 *                       than letting a visitor type into a conversation nobody will answer
 * @param messagesLeft   how many messages this conversation may still hold
 * @param operatorActive a person from the company has taken the conversation over
 */
public record ChatReplyResponse(String reply, boolean ended, int messagesLeft, boolean operatorActive) {
}
