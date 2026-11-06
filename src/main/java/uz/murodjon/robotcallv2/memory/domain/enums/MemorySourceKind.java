package uz.murodjon.robotcallv2.memory.domain.enums;

/** Who wrote a remembered fact. */
public enum MemorySourceKind {
    /** The post-call summary of a phone call. */
    CALL,
    /** The end of a chat conversation. */
    CHAT,
    /** An agent tool, mid-conversation (catalog search, flat search, an agent function). */
    TOOL,
    /** A person, by hand. */
    OPERATOR
}
