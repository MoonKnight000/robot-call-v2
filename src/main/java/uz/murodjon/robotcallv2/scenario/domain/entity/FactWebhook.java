package uz.murodjon.robotcallv2.scenario.domain.entity;

import java.util.Map;

/**
 * Where a scenario fetches this call's facts from, moments before the number is dialled.
 *
 * <p>A campaign's facts arrive once, in the CSV that created its targets, and then age.
 * A list uploaded on Monday still says 1 500 000 so'm on Friday, and the bot says it —
 * to somebody who paid on Wednesday. The CRM overlay ({@code CallContextMapper#merge})
 * covers the fields the CRM happens to model; this covers the rest, and covers companies
 * whose system of record is not a CRM this project has an adapter for.
 *
 * <p>The endpoint is called with the call's identifying data and answers with a flat JSON
 * object. Only keys the scenario's {@code factSchema} declares are read from the answer,
 * so an endpoint cannot introduce facts the scenario never planned to speak — and every
 * text value still passes through {@code PromptSafeText} on its way into
 * {@code CallContext}, like any other untrusted fact.
 *
 * @param url       {@code http}/{@code https} endpoint. Screened against private and
 *                  loopback addresses at call time — this URL is written by a tenant, and
 *                  an unscreened one is a request this server makes on their behalf into
 *                  its own network
 * @param method    {@code POST} (default) or {@code GET}. POST carries the call data in a
 *                  JSON body; GET appends {@code ?phone=&clientId=&campaignId=}
 * @param headers   sent as written — typically an {@code Authorization} for the customer's
 *                  own API. Stored inside the scenario definition, so anyone in the company
 *                  with scenario read access can see them: use a token scoped to this one
 *                  endpoint, not a general-purpose key
 * @param timeoutMs how long the dialer waits. 0 or less means the default; the ceiling is
 *                  deliberately low, because this runs between claiming a target and
 *                  originating its call and a slow endpoint would throttle the whole
 *                  campaign rather than just its own call
 */
public record FactWebhook(
        String url,
        String method,
        Map<String, String> headers,
        int timeoutMs
) {
    public FactWebhook(String url) {
        this(url, null, Map.of(), 0);
    }
}
