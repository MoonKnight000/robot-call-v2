package uz.murodjon.robotcallv2.aiagent.application.dto;

import java.util.Map;

/**
 * How a test send went. A failing endpoint is a result here, not an error of this request.
 *
 * @param ok        the endpoint answered 2xx (and, for an initiation test, with JSON or no body)
 * @param latencyMs how long the send took
 * @param reply     an initiation test's JSON reply; null for a post-call test, a failure or an empty body
 * @param error     why it failed — the transport's message with secret values masked; null when ok
 */
public record AgentWebhookTestResponse(boolean ok, long latencyMs, Map<String, Object> reply, String error) {
}
