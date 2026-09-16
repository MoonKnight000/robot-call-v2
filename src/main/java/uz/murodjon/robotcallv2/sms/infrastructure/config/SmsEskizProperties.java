package uz.murodjon.robotcallv2.sms.infrastructure.config;

/**
 * Eskiz gateway login — {@code voice-agent.sms.eskiz.*} ({@link SmsProperties}).
 *
 * @param email       account login; blank leaves the gateway unconfigured
 * @param password    account password
 * @param baseUrl     API root, {@code https://notify.eskiz.uz/api} unless overridden
 * @param callbackUrl where Eskiz reports delivery status; blank asks for no report
 */
public record SmsEskizProperties(String email, String password, String baseUrl, String callbackUrl) {

    private static final String DEFAULT_BASE_URL = "https://notify.eskiz.uz/api";

    public SmsEskizProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_BASE_URL;
        } else if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
    }

    /** Whether both halves of the login are set — without them no SMS can be sent. */
    public boolean hasCredentials() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
