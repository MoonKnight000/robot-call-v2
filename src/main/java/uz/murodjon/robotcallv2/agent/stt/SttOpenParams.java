package uz.murodjon.robotcallv2.agent.stt;

import java.util.List;

/**
 * Everything that decides what a recognition stream is: two streams opened with equal
 * parameters are interchangeable. A stream opened at ring time is handed to the call that
 * answers only if the parameters the call then asks for are equal to these — otherwise it
 * is closed and the call opens its own, as it always has.
 *
 * @param provider            {@link SttProvider#name()}
 * @param alternativeLanguages the call's detect list
 * @param hints               the hint list as the stream was opened with it
 * @param model               the recognition model, or null for the provider's own
 */
public record SttOpenParams(
        String provider,
        String language,
        List<String> alternativeLanguages,
        List<String> hints,
        String model,
        boolean externalEndpointing
) {

    public SttOpenParams {
        alternativeLanguages = alternativeLanguages == null ? List.of() : List.copyOf(alternativeLanguages);
        hints = hints == null ? List.of() : List.copyOf(hints);
        model = model == null || model.isBlank() ? null : model.trim();
    }
}
