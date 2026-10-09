package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * Who talks to the clients a campaign dials (docs/OPERATOR-DIALER-ANALYTICS-PLAN.md §1).
 * Fixed when the campaign is created: a run half dialled by the AI and half by operators
 * would mix two kinds of outcome in one report.
 */
public enum DialMode {
    /** The campaign's AI agent speaks; the scheduled dialer places every call. */
    AI,
    /** An operator from the campaign's queue takes the next client's card and dials it themselves. */
    PREVIEW,
    /** One call per free operator of the queue; an answered client is joined to that operator. */
    PROGRESSIVE,
    /** More calls than free operators, paced by the answer rate; a client no operator is left for is taken by the AI agent. */
    PREDICTIVE;

    /** Whether the campaign's calls are placed by the AI dialer rather than for operators. */
    public boolean placesAiCalls() {
        return this == AI;
    }
}
