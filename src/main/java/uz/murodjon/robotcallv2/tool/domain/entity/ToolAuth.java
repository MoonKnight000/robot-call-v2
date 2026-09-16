package uz.murodjon.robotcallv2.tool.domain.entity;

import uz.murodjon.robotcallv2.tool.domain.enums.ToolAuthType;

/**
 * A tool's own authentication (UNIVERSAL-OPERATOR-PLAN B.5). Names secrets, never holds them:
 * the id and the secret stay in the company's encrypted store and are read on each call.
 *
 * @param tokenUrl           the token endpoint; may hold {@code {{secrets.KEY}}}
 * @param clientIdSecret     the secret key holding the OAuth client id
 * @param clientSecretSecret the secret key holding the OAuth client secret
 * @param scope              space-separated scopes to ask for; null asks for none
 */
public record ToolAuth(
        ToolAuthType type,
        String tokenUrl,
        String clientIdSecret,
        String clientSecretSecret,
        String scope
) {
}
