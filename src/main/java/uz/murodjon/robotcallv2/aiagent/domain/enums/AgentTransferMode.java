package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** How a TRANSFER_CALL function hands the caller over. */
public enum AgentTransferMode {
    COLD,
    /** The operator is briefed (who is calling, what was said) while the phone still rings. */
    WARM
}
