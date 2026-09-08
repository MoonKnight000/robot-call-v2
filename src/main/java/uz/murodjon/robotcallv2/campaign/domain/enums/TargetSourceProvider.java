package uz.murodjon.robotcallv2.campaign.domain.enums;

/**
 * How a campaign's target source produces its list.
 */
public enum TargetSourceProvider {

    /**
     * One HTTP request to the URL the company configured, whose answer is an array of rows
     * with a phone in each. The default, and everything {@code PUT /api/campaigns/{id}/target-source}
     * can create.
     */
    GENERIC,

    /**
     * Several requests, configured on the campaign screen: one that lists rows and any
     * number that are then called per row to fill in what the list did not carry. The
     * general form of what {@link #UYSOT_DEBTORS} does for one CRM in particular.
     */
    CHAINED,

    /**
     * Today's overdue debtors, read out of Uysot's Open API. Configured by
     * {@code voice-agent.crm.*} rather than by the source's own URL, because reaching a
     * phone number there takes three chained reads and not one.
     *
     * <p>Kept as its own provider although {@link #CHAINED} can now express the same
     * chain: it is what the seeded daily campaign runs on, and it reads its credentials
     * from the company's Uysot OAuth connection rather than from a header typed into a
     * form.
     */
    UYSOT_DEBTORS
}
