package uz.murodjon.uysotvoice.integration.enums;

/**
 * How a company connects to a given {@code CrmProvider} (report #10 catalog). Every
 * provider today is {@link #OAUTH}; {@link #API_TOKEN} exists for a future connector
 * that authenticates with a pasted token instead of a redirect+consent flow.
 */
public enum CrmAuthMethod {
    OAUTH,
    API_TOKEN
}
