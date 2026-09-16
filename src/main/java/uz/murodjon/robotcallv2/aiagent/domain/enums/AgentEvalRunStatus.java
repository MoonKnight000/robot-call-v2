package uz.murodjon.robotcallv2.aiagent.domain.enums;

/** Where an agent evaluation run stands. */
public enum AgentEvalRunStatus {
    RUNNING,
    DONE,
    /** The run itself broke off; the cases played before it are kept. */
    FAILED
}
