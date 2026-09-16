package uz.murodjon.robotcallv2.agent.queue;

/** Where a queued call is, as the dispatcher and the call's own thread both see it. */
public enum WaitingCallState {
    /** In line, nobody ringing. */
    WAITING,
    /** One or more operators are ringing for it. */
    RINGING,
    /** An operator answered; the whisper plays and the bridge is being joined. */
    CONNECTING,
    /** Caller and operator are talking. */
    CONNECTED,
    /** The operator hung up (or was transferred away); the caller is still on the line. */
    OPERATOR_LEFT,
    /** Out of the queue: answered and done, overflowed, called back, or the caller left. */
    CANCELLED
}
