package uz.murodjon.robotcallv2.billing.infrastructure.config;

/**
 * Click merchant settings — {@code voice-agent.billing.payment.click.*} ({@link PaymentProperties}).
 *
 * @param enabled    off, and Click top-ups are refused and Click's callbacks answered -1
 * @param serviceId  the service id from the Click merchant cabinet
 * @param merchantId the merchant id from the same cabinet
 * @param secretKey  the service's secret key, which every callback's {@code sign_string} is computed over
 * @param returnUrl  where Click sends the payer after paying; blank for Click's own page
 */
public record PaymentClickProperties(
        boolean enabled,
        String serviceId,
        String merchantId,
        String secretKey,
        String returnUrl
) {
    /** Whether Click can be used at all: switched on, and the service, merchant and key all set. */
    public boolean configured() {
        return enabled
                && serviceId != null && !serviceId.isBlank()
                && merchantId != null && !merchantId.isBlank()
                && secretKey != null && !secretKey.isBlank();
    }
}
