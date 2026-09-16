package uz.murodjon.robotcallv2.billing.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentCheckoutUrlsTest {

    @Test
    void paymeEncodesMerchantAccountAndAmountInTiyin() {
        String url = PaymentCheckoutUrls.payme("https://checkout.paycom.uz", "merchant-1", "PAY-1A2B3C4D",
                5_000, null);

        assertThat(url).startsWith("https://checkout.paycom.uz/");
        assertThat(decodePaymeParams(url)).isEqualTo("m=merchant-1;ac.payment_id=PAY-1A2B3C4D;a=500000");
    }

    @Test
    void paymeAppendsTheReturnUrlWhenSet() {
        String url = PaymentCheckoutUrls.payme("https://checkout.paycom.uz", "merchant-1", "PAY-1A2B3C4D",
                5_000, "https://app.example.uz/billing");

        assertThat(decodePaymeParams(url))
                .isEqualTo("m=merchant-1;ac.payment_id=PAY-1A2B3C4D;a=500000;c=https://app.example.uz/billing");
    }

    @Test
    void clickCarriesTheAmountInUzsAndThePaymentIdAsTransactionParam() {
        String url = PaymentCheckoutUrls.click("81", "98765", "PAY-1A2B3C4D", 5_000, null);

        assertThat(url).isEqualTo("https://my.click.uz/services/pay?service_id=81&merchant_id=98765"
                + "&amount=5000&transaction_param=PAY-1A2B3C4D");
    }

    @Test
    void clickUrlEncodesTheReturnUrl() {
        String url = PaymentCheckoutUrls.click("81", "98765", "PAY-1A2B3C4D", 5_000,
                "https://app.example.uz/billing?paid=1");

        assertThat(url).endsWith("&return_url=https%3A%2F%2Fapp.example.uz%2Fbilling%3Fpaid%3D1");
    }

    private static String decodePaymeParams(String url) {
        String encoded = url.substring("https://checkout.paycom.uz/".length());
        return new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
    }
}
