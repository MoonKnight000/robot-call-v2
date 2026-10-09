package uz.murodjon.robotcallv2.callrecord.domain.enums;

/** Where a {@code CallReanalysisJob} stands. */
public enum CallReanalysisStatus {
    /** Waiting for the worker. */
    QUEUED,
    /** The worker has started on it; it goes through the calls a batch at a time. */
    RUNNING,
    /** Every call in the range was gone through. */
    COMPLETED,
    /** Stopped from the panel; the calls already done keep their new analysis. */
    CANCELLED,
    /** Stopped by the worker — the balance ran out, or the agent is gone; see {@code lastError}. */
    FAILED
}
