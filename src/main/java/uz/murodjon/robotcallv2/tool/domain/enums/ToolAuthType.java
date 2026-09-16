package uz.murodjon.robotcallv2.tool.domain.enums;

/**
 * How a tool authenticates beyond what its headers and address already carry
 * (UNIVERSAL-OPERATOR-PLAN B.5). An API key in a header or in the query string and a fixed
 * bearer token need no type of their own: {@code {{secrets.KEY}}} in {@code apiHeaders} or
 * {@code apiUrl} is enough.
 */
public enum ToolAuthType {
    /** The tool gets its own access token from the token endpoint, as an OAuth 2.0 client. */
    OAUTH2_CLIENT_CREDENTIALS
}
