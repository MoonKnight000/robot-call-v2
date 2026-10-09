package uz.murodjon.robotcallv2.copilot.application.port.output;

/**
 * What the model adapter calls when the model asks for a tool. Never throws: a refusal comes
 * back as text the model can read and act on.
 */
@FunctionalInterface
public interface CopilotToolInvoker {

    /**
     * @param argumentsJson the model's arguments, as a JSON object
     * @return the tool's answer, or why it was refused
     */
    String callTool(String name, String argumentsJson);
}
