package uz.murodjon.robotcallv2.agent.dialog;

/**
 * What one test run of a tool produced.
 *
 * @param result  exactly what the model would be handed
 * @param rawBody the endpoint's answer as it came, before any mapping; null when the request
 *                never got an answer
 */
public record ToolTestRun(String result, String rawBody) {
}
