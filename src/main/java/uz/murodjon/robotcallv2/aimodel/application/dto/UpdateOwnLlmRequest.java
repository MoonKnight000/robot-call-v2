package uz.murodjon.robotcallv2.aimodel.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param baseUrl      the OpenAI-compatible root, {@code /v1} included; public https only
 * @param model        the model id the endpoint serves
 * @param fastModel    optional cheaper model for one-word answers; null uses {@code model}
 * @param apiKeySecret the name of the company secret holding the API key
 */
public record UpdateOwnLlmRequest(
        @NotBlank @Size(max = 500) String baseUrl,
        @NotBlank @Size(max = 100) String model,
        @Size(max = 100) String fastModel,
        @NotBlank @Size(max = 100) String apiKeySecret
) {
}
