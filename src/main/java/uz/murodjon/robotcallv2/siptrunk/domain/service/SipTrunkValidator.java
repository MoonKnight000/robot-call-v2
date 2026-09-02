package uz.murodjon.robotcallv2.siptrunk.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

public final class SipTrunkValidator {

    private SipTrunkValidator() {
    }

    public static boolean isManaged(String pjsipEndpoint, String host) {
        boolean hasManual = pjsipEndpoint != null && !pjsipEndpoint.isBlank();
        boolean hasManaged = host != null && !host.isBlank();
        if (hasManual && hasManaged) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_MODE_CONFLICT);
        }
        if (!hasManual && !hasManaged) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_MODE_MISSING);
        }
        return hasManaged;
    }

    public static void requireUsername(String sipUsername) {
        if (sipUsername == null || sipUsername.isBlank()) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_USERNAME_REQUIRED);
        }
    }

    public static void requireValidTransport(SipTrunkTransport transport) {
        if (transport != null && transport != SipTrunkTransport.UDP) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_TRANSPORT_UNSUPPORTED, transport);
        }
    }

    public static int portOrDefault(Integer port) {
        return port != null && port > 0 ? port : 5060;
    }

    public static SipTrunkTransport transportOrDefault(SipTrunkTransport transport) {
        return transport != null ? transport : SipTrunkTransport.UDP;
    }
}
