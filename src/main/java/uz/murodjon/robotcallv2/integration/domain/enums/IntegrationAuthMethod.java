package uz.murodjon.robotcallv2.integration.domain.enums;

/**
 * How a company proves to an integration that the account is its own. An integration lists the
 * methods it supports: Uysot takes OAuth or a token, Eskiz a login and password, Google Drive
 * Google's OAuth.
 */
public enum IntegrationAuthMethod {
    /** The provider's consent screen. */
    OAUTH,
    /** An access token the company copies from the provider. */
    API_TOKEN,
    /** The account's login and password. */
    LOGIN_PASSWORD
}
