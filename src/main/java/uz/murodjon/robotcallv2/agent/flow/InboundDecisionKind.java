package uz.murodjon.robotcallv2.agent.flow;

/** What happens to an inbound call before anything is answered. */
public enum InboundDecisionKind {
    /** No enabled route for the number dialled. */
    NO_ROUTE,
    /** A caller rule refuses the caller. */
    REJECT,
    /** Outside the route's hours (or it has nobody to answer): say the message, hang up. */
    CLOSED,
    /** Run the route's published call flow. */
    FLOW,
    /** Answer with the route's AI agent, as before call flows existed. */
    AGENT
}
