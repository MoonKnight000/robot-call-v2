package uz.murodjon.robotcallv2.operator.domain.entity;

/**
 * Everything the Asterisk config needs to know about one operator endpoint — the account
 * with its password decrypted and the name and extension it is shown under.
 */
public record OperatorSipEndpoint(
        long userId,
        long companyId,
        String endpointName,
        String password,
        boolean webrtc,
        String displayName,
        String extension
) {
}
