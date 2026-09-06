package uz.murodjon.robotcallv2.scenario.domain.entity;

/**
 * What a {@link FactWebhook} is told about the call it is being asked to describe.
 *
 * <p>Serialized straight to the request body, so the field names are the contract the
 * customer's endpoint reads. A record rather than a parameter list because the two
 * directions fill different halves of it and positional arguments would make the null
 * halves invisible at the call site.
 *
 * @param direction    {@code "inbound"} or {@code "outbound"} — the endpoint needs it to
 *                     know whether {@code phone} is a caller or a target
 * @param phone        the other party's number: who rang in, or who is about to be rung
 * @param dialedNumber inbound only — which of the company's numbers the caller reached, so
 *                     an endpoint serving several lines can tell them apart
 * @param clientId     outbound only — the CRM id the campaign target carries, when it has one
 * @param campaignId   outbound only
 */
public record FactWebhookRequest(
        String direction,
        String phone,
        String dialedNumber,
        Long clientId,
        Long campaignId
) {
    public static FactWebhookRequest inbound(String callerNumber, String dialedNumber) {
        return new FactWebhookRequest("inbound", callerNumber, dialedNumber, null, null);
    }

    public static FactWebhookRequest outbound(String phone, Long clientId, Long campaignId) {
        return new FactWebhookRequest("outbound", phone, null, clientId, campaignId);
    }
}
