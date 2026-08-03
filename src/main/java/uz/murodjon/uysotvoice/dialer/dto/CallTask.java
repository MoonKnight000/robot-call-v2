package uz.murodjon.uysotvoice.dialer.dto;

/**
 * A single outbound-call instruction published to RabbitMQ and consumed to originate
 * the call (PROJECT.md §5.2, §10). Serialized as JSON.
 *
 * @param campaignId  owning campaign
 * @param targetId    campaign_target being dialed
 * @param clientId    CRM client id (for the CRM note write-back)
 * @param phone       number to dial through the trunk
 * @param language    BCP-47 conversation language (null → campaign default)
 * @param ttsVoice    catalog id of the campaign's chosen voice (§2.5); null → the
 *                    configured routing
 * @param contextData raw {@code context_data} JSON with the debtor facts
 * @param scenarioId  the campaign's bound scenario row (ROADMAP A.3)
 * @param companyId   the campaign's owning company (ROADMAP B.1) — decides which SIP
 *                    trunk originates this call (ROADMAP B.3)
 * @param disclosureEnabled whether this campaign's calls open with the §11.1
 *                    disclosure
 */
public record CallTask(
        Long campaignId,
        Long targetId,
        Long clientId,
        String phone,
        String language,
        String ttsVoice,
        String contextData,
        Long scenarioId,
        long companyId,
        boolean disclosureEnabled
) {
}
