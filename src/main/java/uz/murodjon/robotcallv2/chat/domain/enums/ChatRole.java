package uz.murodjon.robotcallv2.chat.domain.enums;

/**
 * Who wrote a chat message: the client, the agent, or a person from the company who took the
 * conversation over from the agent ({@code ChatSession.operatorUserId}).
 */
public enum ChatRole {
    CLIENT,
    AGENT,
    OPERATOR
}
