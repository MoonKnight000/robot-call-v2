package uz.murodjon.robotcallv2.webhook.application.port.output;

import java.util.Map;

/**
 * Delivery of a JSON payload to a URL a customer configured.
 *
 * <p>Every outbound webhook this application sends goes through here: the agent's
 * initiation and post-call hooks, and the per-action hooks a call's follow-up runs. They
 * are three callers of one operation, so the HTTP client, its timeouts and its content
 * type are settled once, in the adapter behind this interface, and no service holds a
 * {@code RestClient} of its own.
 *
 * <p>Both methods let the transport's exception out. A webhook is best-effort — the call
 * it describes has already happened — so the caller decides whether a failure is worth a
 * log line or worth failing for; that is not something this port can know.
 *
 * <p>{@code signingSecret} is the company's shared secret for proving the body came from
 * here. It is passed in rather than looked up because only a service knows whose call
 * this is. When it is null the request goes unsigned, which is what a company that has
 * not set the secret gets.
 */
public interface WebhookSenderPort {

    /** POSTs {@code payload} as JSON and discards whatever comes back. */
    void post(String url, Map<String, String> headers, Map<String, Object> payload, String signingSecret);

    /**
     * POSTs {@code payload} as JSON and reads the reply as a JSON object.
     *
     * @return the reply, or {@code null} when the endpoint answered with no body
     */
    Map<String, Object> postForObject(String url, Map<String, String> headers, Map<String, Object> payload,
                                      String signingSecret);
}
