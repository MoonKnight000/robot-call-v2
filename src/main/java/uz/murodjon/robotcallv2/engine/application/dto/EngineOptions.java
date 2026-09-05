package uz.murodjon.robotcallv2.engine.application.dto;

import java.util.List;

/** Options for engine settings screen. */
public record EngineOptions(
        List<String> stt,
        List<String> tts,
        List<String> realtime,
        List<String> pipecatStt,
        List<String> pipecatLlm,
        List<String> pipecatTts
) {
    public EngineOptions(List<String> stt, List<String> tts, List<String> realtime) {
        this(stt, tts, realtime, List.of(), List.of(), List.of());
    }
}
