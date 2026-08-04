package uz.murodjon.uysotvoice.integration.enums;

/**
 * {@code crm_integration.provider} (§11 settings, report #10 catalog). Only {@link
 * #UYSOT} has a working OAuth flow ({@code CrmIntegrationService}) — {@link #BITRIX24}/
 * {@link #AMOCRM} exist so the catalog ({@code GET
 * /api/settings/integrations/catalog}) can list them as "coming soon"; wiring their
 * actual OAuth endpoints is a separate follow-up once those are known.
 */
public enum CrmProvider {
    UYSOT,
    BITRIX24,
    AMOCRM
}
