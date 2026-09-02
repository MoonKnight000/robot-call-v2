package uz.murodjon.robotcallv2.engine.application.dto;

import java.util.List;

/** Options for engine settings screen. */
public record EngineOptions(
        List<String> stt,
        List<String> tts,
        List<String> realtime
) {
}
