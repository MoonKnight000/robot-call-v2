package uz.murodjon.robotcallv2.chat.application.dto;

/**
 * A chat that has just been opened: the handle the browser sends back with every message,
 * and the agent's opening line.
 *
 * @param maxMessages how many messages this conversation may hold in all, so the widget can
 *                    show it rather than discover it at the limit
 */
public record StartChatResponse(String sessionKey, String greeting, String language, int maxMessages) {
}
