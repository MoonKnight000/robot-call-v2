package uz.murodjon.robotcallv2.chat.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param publicBaseUrl where this deployment answers, as the internet sees it
 *                      ({@code https://voice.example.uz}). The update address is built from
 *                      it; Telegram will not send updates to a plain http address
 */
public record RegisterWebhookRequest(@NotBlank @Size(max = 500) String publicBaseUrl) {
}
