package uz.murodjon.robotcallv2.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

/**
 * PUT /api/settings/integrations/account body — amoCRM, Kommo or Bitrix24 (§11).
 *
 * @param provider    {@code AMOCRM}, {@code KOMMO} or {@code BITRIX24}; Uysot connects
 *                    through OAuth instead
 * @param baseUrl     amoCRM/Kommo: the account address, e.g. {@code acme.amocrm.ru} or
 *                    {@code https://acme.kommo.com}; Bitrix24: the inbound webhook URL,
 *                    {@code https://<portal>/rest/<user>/<code>/}
 * @param accessToken amoCRM/Kommo: the long-lived token of the account's private
 *                    integration; Bitrix24: none — the webhook URL is the credential
 */
public record ConnectCrmAccountRequest(
        @NotNull CrmProvider provider,
        @NotBlank String baseUrl,
        String accessToken
) {
}
