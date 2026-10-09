package uz.murodjon.robotcallv2.scenario.domain.entity;

/**
 * A question the caller may ask at any stage — "who are you?", "where did you get my
 * number?" — and how it is answered. The answer does not move the FSM: the call stays in
 * the stage it was in and goes on from there, which is what a stage of its own could not
 * do without an edge back from it to every other stage.
 *
 * @param question what the caller asks, as an example of the intent rather than a pattern
 *                 to match word for word
 * @param answer   what the agent says, or how it answers, in the call's language
 */
public record GlobalNode(String question, String answer) {
}
