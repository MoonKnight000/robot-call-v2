package uz.murodjon.robotcallv2.tool.application.dto;

import uz.murodjon.robotcallv2.tool.domain.entity.ToolSpeech;

/**
 * {@link ToolSpeech} as the tool endpoints read and write it. On a create a null
 * {@code forcePreToolSpeech} is on and a null {@code disableInterruptions} off; on an update a
 * null field keeps the tool's value.
 */
public record ToolSpeechDto(
        Boolean disableInterruptions,
        Boolean forcePreToolSpeech,
        String preToolSpeech
) {
    public static ToolSpeechDto of(ToolSpeech speech) {
        return new ToolSpeechDto(speech.disableInterruptions(), speech.forcePreToolSpeech(), speech.preToolSpeech());
    }
}
