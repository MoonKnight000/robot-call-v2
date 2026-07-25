package uz.murodjon.uysotvoice.dialer;

/**
 * A single outbound-call instruction published to RabbitMQ and consumed to originate
 * the call (PROJECT.md §5.2, §10). Serialized as JSON.
 *
 * @param campaignId  owning campaign
 * @param targetId    campaign_target being dialed
 * @param clientId    CRM client id (for the CRM note write-back)
 * @param phone       number to dial through the trunk
 * @param language    BCP-47 conversation language (null → campaign default)
 * @param contextData raw {@code context_data} JSON with the debtor facts
 */
public record CallTask(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String contextData
) {
}
