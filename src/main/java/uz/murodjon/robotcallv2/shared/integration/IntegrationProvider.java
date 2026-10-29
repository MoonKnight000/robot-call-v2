package uz.murodjon.robotcallv2.shared.integration;

/**
 * Every system the platform connects to or plans to, one catalog entry each. The platform
 * switches providers on and off per company, so the company and the integration feature both
 * need the same names — hence here and not in either.
 *
 * <p>{@code available} is whether a connector exists yet; the rest are listed in the catalog as
 * "coming soon" so a company sees what is planned.
 */
public enum IntegrationProvider {

    UYSOT(IntegrationCategory.CRM, true),
    AMOCRM(IntegrationCategory.CRM, true),
    KOMMO(IntegrationCategory.CRM, true),
    BITRIX24(IntegrationCategory.CRM, true),
    GOOGLE_DRIVE(IntegrationCategory.KNOWLEDGE_BASE, false),
    NOTION(IntegrationCategory.KNOWLEDGE_BASE, false),
    ASTERISK(IntegrationCategory.TELEPHONY, false);

    private final IntegrationCategory category;
    private final boolean available;

    IntegrationProvider(IntegrationCategory category, boolean available) {
        this.category = category;
        this.available = available;
    }

    public IntegrationCategory category() {
        return category;
    }

    /** Whether a connector exists, whatever the platform has switched on for a company. */
    public boolean available() {
        return available;
    }
}
