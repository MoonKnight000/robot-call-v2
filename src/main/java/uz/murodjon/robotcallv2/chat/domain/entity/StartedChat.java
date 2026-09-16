package uz.murodjon.robotcallv2.chat.domain.entity;

/** A conversation that has just been opened, and the line the agent opened it with. */
public record StartedChat(ChatSession session, String greeting) {
}
