package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * How a speech-to-speech engine learns the call's facts — the debt amount, the due date,
 * the contract number (§4.4).
 *
 * <p>Only a realtime call has to choose. The cascade pipeline sees every sentence before
 * it is spoken and withholds one whose figures do not match the facts; a realtime engine
 * speaks straight from audio, so the only prevention left is what it was given.
 */
public enum RealtimeFactsDelivery {

    /**
     * Written into the instructions, as the cascade pipeline does. Fastest — the figure is
     * already there when the model needs it — and the engine can misremember or embellish
     * it; the audit only notices afterwards.
     */
    PROMPT,

    /**
     * Kept out of the instructions and fetched with {@code getCallFact} at the moment it is
     * about to be said. Costs a tool round trip before a figure and leaves the model nothing
     * to misremember — the right setting for money on a recorded line.
     */
    TOOL
}
