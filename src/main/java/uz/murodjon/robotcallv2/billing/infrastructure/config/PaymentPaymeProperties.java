package uz.murodjon.robotcallv2.billing.infrastructure.config;

/**
 * Payme merchant settings — {@code voice-agent.billing.payment.payme.*} ({@link PaymentProperties}).
 *
 * @param enabled     off, and Payme top-ups are refused and Payme's callbacks answered -32504
 * @param merchantId  the cashbox id from the Payme merchant cabinet
 * @param key         the cashbox key Payme authenticates its callbacks with
 *                    (HTTP Basic {@code Paycom:<key>}); the test key against the sandbox
 * @param checkoutUrl checkout host, {@code https://checkout.paycom.uz} unless overridden
 *                    ({@code https://test.paycom.uz} for the sandbox)
 * @param returnUrl   where Payme sends the payer after paying; blank for Payme's own page
 * @param ikpuCode    fiscal product code (IKPU / MXIK) printed on the receipt; blank sends no
 *                    receipt {@code detail} at all
 * @param packageCode the package code registered with that IKPU code
 * @param vatPercent  VAT percent printed on the receipt
 */
public record PaymentPaymeProperties(
        boolean enabled,
        String merchantId,
        String key,
        String checkoutUrl,
        String returnUrl,
        String ikpuCode,
        String packageCode,
        int vatPercent
) {
    private static final String DEFAULT_CHECKOUT_URL = "https://checkout.paycom.uz";

    public PaymentPaymeProperties {
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            checkoutUrl = DEFAULT_CHECKOUT_URL;
        } else if (checkoutUrl.endsWith("/")) {
            checkoutUrl = checkoutUrl.substring(0, checkoutUrl.length() - 1);
        }
    }

    /** Whether Payme can be used at all: switched on, and both halves of the cashbox login set. */
    public boolean configured() {
        return enabled && merchantId != null && !merchantId.isBlank() && key != null && !key.isBlank();
    }

    /** Whether CheckPerformTransaction sends a fiscal receipt. */
    public boolean sendsReceipt() {
        return ikpuCode != null && !ikpuCode.isBlank();
    }
}
