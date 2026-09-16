package uz.murodjon.robotcallv2.integration.domain.entity;

import uz.murodjon.robotcallv2.integration.domain.enums.CrmProvider;

/**
 * How to reach one company's CRM right now: which product, at what address, with what
 * credential. Decrypted, so it lives for the length of one request and is never stored.
 *
 * @param baseUrl     amoCRM/Kommo: the account address ({@code https://acme.amocrm.ru});
 *                    Bitrix24: the inbound webhook URL, whose path carries its secret;
 *                    Uysot: the platform's configured API address. {@code null} for a
 *                    provider the company chose but has not (or no longer) connected
 * @param accessToken sent with every request; {@code null} when the provider needs none
 *                    (a Bitrix24 webhook) or there is none
 */
public record CrmConnection(CrmProvider provider, String baseUrl, String accessToken) {

    /** The provider only: the other two are credentials, and a record's default prints them. */
    @Override
    public String toString() {
        return "CrmConnection[provider=" + provider + "]";
    }
}
