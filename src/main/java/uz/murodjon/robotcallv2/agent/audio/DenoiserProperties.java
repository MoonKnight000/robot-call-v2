package uz.murodjon.robotcallv2.agent.audio;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Neural noise suppression on the caller's leg ({@link NeuralDenoiser}, GTCRN). Bound from
 * {@code voice-agent.denoiser.*}.
 *
 * <p>When on and the model loads, it replaces {@link TelephonyNoiseCanceller}'s broadband
 * gate for every call whose agent has noise cancellation on — the gate passes noise
 * <em>under</em> speech, which is exactly what a recognizer mishears. Off, or with the file
 * missing, every call keeps the broadband gate.
 *
 * @param enabled   master switch
 * @param modelPath path to the streaming GTCRN ONNX export ({@code gtcrn_simple.onnx})
 */
@ConfigurationProperties(prefix = "voice-agent.denoiser")
public record DenoiserProperties(boolean enabled, String modelPath) {
}
