package uz.murodjon.robotcallv2.chat.domain.entity;

/**
 * One answered message: what the agent replied, and the conversation as it now stands —
 * its stage, what its tools recorded, and whether it is over.
 */
public record ChatTurn(String reply, ChatSession session) {
}
