package uz.murodjon.robotcallv2.agent.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * One scripted caller for {@link DialogSimulationRunner}: who answers the phone, what they
 * are like, and how the call is supposed to end.
 *
 * <p>The interesting field is {@link #prompt()} — it is the whole persona, written as
 * instructions to the model playing the caller ("you have no money this month, you refuse
 * politely, you eventually offer the 15th"). A suite is a folder of these: the refusal, the
 * wrong number, the caller who answers in Russian, the one who never stops talking, the
 * one who agrees immediately. What makes it a regression net rather than a demo is
 * {@link #expectDisposition()}: a prompt change that stops the agent recording a refusal
 * shows up as that persona failing, not as a transcript somebody has to read.
 *
 * @param id                short name, used in the report
 * @param prompt            the instructions given to the model playing the caller
 * @param facts             the call's facts, as the CRM would have supplied them, keyed by
 *                          the scenario's {@code factSchema} names
 * @param expectDisposition how the call should end ({@code PROMISE_TO_PAY},
 *                          {@code WRONG_NUMBER}, …), or null to record whatever happens
 *                          without judging it
 * @param expectStage       the stage the conversation should have reached, or null
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SimulationPersona(
        String id,
        String prompt,
        Map<String, Object> facts,
        String expectDisposition,
        String expectStage
) {

    public SimulationPersona {
        facts = facts == null ? Map.of() : Map.copyOf(facts);
    }
}
