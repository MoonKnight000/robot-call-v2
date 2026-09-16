package uz.murodjon.robotcallv2.billing.domain.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Where a payer is sent to pay a top-up. The formats are the providers', not ours, so they
 * are built here, apart from the service, where they can be checked on their own.
 */
public final class PaymentCheckoutUrls {

    private static final String CLICK_PAY_URL = "https://my.click.uz/services/pay";

    private PaymentCheckoutUrls() {
    }

    /**
     * Payme's checkout link:
     * {@code <checkoutUrl>/base64(m=<merchantId>;ac.payment_id=<paymentId>;a=<tiyin>[;c=<returnUrl>])}.
     *
     * @param checkoutUrl the checkout host, without a trailing slash
     */
    public static String payme(String checkoutUrl, String merchantId, String paymentId, long amountUzs,
                               String returnUrl) {
        StringBuilder params = new StringBuilder()
                .append("m=").append(merchantId)
                .append(";ac.payment_id=").append(paymentId)
                .append(";a=").append(amountUzs * 100);
        if (returnUrl != null && !returnUrl.isBlank()) {
            params.append(";c=").append(returnUrl);
        }
        return checkoutUrl + "/" + Base64.getEncoder().encodeToString(params.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** Click's pay link; the amount is in whole UZS and {@code transaction_param} comes back as {@code merchant_trans_id}. */
    public static String click(String serviceId, String merchantId, String paymentId, long amountUzs,
                               String returnUrl) {
        StringBuilder url = new StringBuilder(CLICK_PAY_URL)
                .append("?service_id=").append(encode(serviceId))
                .append("&merchant_id=").append(encode(merchantId))
                .append("&amount=").append(amountUzs)
                .append("&transaction_param=").append(encode(paymentId));
        if (returnUrl != null && !returnUrl.isBlank()) {
            url.append("&return_url=").append(encode(returnUrl));
        }
        return url.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
