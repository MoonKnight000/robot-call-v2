package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** Where the text of a SEND_SMS function comes from. */
public enum AgentSmsContentMode {
    /** The content is an instruction; the model writes the text. */
    PROMPT,
    /** The content is the text itself, sent as written with {{variables}} filled in. */
    STATIC
}
