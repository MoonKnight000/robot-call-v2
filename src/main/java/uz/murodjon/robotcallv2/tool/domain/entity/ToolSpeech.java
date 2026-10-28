package uz.murodjon.robotcallv2.tool.domain.entity;

/**
 * What the caller hears while a tool runs.
 *
 * @param disableInterruptions whether the caller cannot talk over the agent while the tool runs
 * @param forcePreToolSpeech   whether {@code preToolSpeech} is said before every call rather
 *                             than left to the model
 * @param preToolSpeech        the sentence that fills the wait, e.g. "Bir daqiqa, tekshiryapman"
 */
public record ToolSpeech(
        boolean disableInterruptions,
        boolean forcePreToolSpeech,
        String preToolSpeech
) {
}
