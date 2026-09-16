package uz.murodjon.robotcallv2.siptrunk.domain.service;

import uz.murodjon.robotcallv2.shared.exception.ErrorCode;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;
import uz.murodjon.robotcallv2.shared.util.PhoneNumbers;
import uz.murodjon.robotcallv2.siptrunk.domain.enums.SipTrunkTransport;

import java.util.List;
import java.util.regex.Pattern;

public final class SipTrunkValidator {

    /*
     * Every trunk field ends up in a dial string (PJSIP/<number>@<endpoint>), in the one
     * generated pjsip_trunks.conf that all tenants share, or in a raw AMI command. A line
     * break, '@', '[' or ';' in any of them lets one company add config sections or AMI
     * actions of its own, so these are allow-lists rather than deny-lists.
     */
    private static final Pattern ENDPOINT_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?$");
    private static final Pattern SIP_USERNAME = Pattern.compile("^[A-Za-z0-9._+-]{1,64}$");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    public static final List<String> SUPPORTED_CODECS = List.of("ulaw", "alaw", "g722", "g729", "gsm", "opus", "ilbc");

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
        if (!isValidUsername(sipUsername)) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_USERNAME_INVALID, sipUsername);
        }
    }

    public static void requireValidEndpointName(String pjsipEndpoint) {
        if (!isValidEndpointName(pjsipEndpoint)) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_ENDPOINT_INVALID, pjsipEndpoint);
        }
    }

    public static void requireValidHost(String host) {
        if (!isValidHost(host)) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_HOST_INVALID, host);
        }
    }

    public static void requireValidPassword(String sipPassword) {
        if (!isValidPassword(sipPassword)) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_PASSWORD_INVALID);
        }
    }

    public static void requireSupportedCodecs(List<String> codecs) {
        if (codecs == null) {
            return;
        }
        for (String codec : codecs) {
            if (!isSupportedCodec(codec)) {
                throw new ValidationException(ErrorCode.SIP_TRUNK_CODEC_UNSUPPORTED, codec, SUPPORTED_CODECS);
            }
        }
    }

    /** A blank caller ID is allowed — the call then goes out with the global one. */
    public static void requireValidCallerId(String callerId) {
        if (callerId != null && !callerId.isBlank() && !PhoneNumbers.isValid(callerId)) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_CALLER_ID_INVALID, callerId);
        }
    }

    public static void requireValidTransport(SipTrunkTransport transport) {
        if (transport != null && transport != SipTrunkTransport.UDP) {
            throw new ValidationException(ErrorCode.SIP_TRUNK_TRANSPORT_UNSUPPORTED, transport);
        }
    }

    public static boolean isValidEndpointName(String pjsipEndpoint) {
        return pjsipEndpoint != null && ENDPOINT_NAME.matcher(pjsipEndpoint).matches();
    }

    public static boolean isValidHost(String host) {
        return host != null && HOST.matcher(host).matches();
    }

    public static boolean isValidUsername(String sipUsername) {
        return sipUsername != null && SIP_USERNAME.matcher(sipUsername).matches();
    }

    public static boolean isValidPassword(String sipPassword) {
        return sipPassword != null && !CONTROL_CHARS.matcher(sipPassword).find();
    }

    public static boolean isSupportedCodec(String codec) {
        return codec != null && SUPPORTED_CODECS.contains(codec.trim().toLowerCase());
    }

    public static int portOrDefault(Integer port) {
        return port != null && port > 0 ? port : 5060;
    }

    public static SipTrunkTransport transportOrDefault(SipTrunkTransport transport) {
        return transport != null ? transport : SipTrunkTransport.UDP;
    }
}
