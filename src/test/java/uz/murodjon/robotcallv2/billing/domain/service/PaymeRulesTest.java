package uz.murodjon.robotcallv2.billing.domain.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentProviderTransaction;
import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.ProviderTransactionState;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PaymeRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");

    /** 5 000 UZS = 500 000 tiyin. */
    private static final PaymentTopup TOPUP = new PaymentTopup(1L, "PAY-1A2B3C4D", 7L, null, 5_000,
            PaymentMethod.PAYME, TopupStatus.PENDING, null, NOW, null);

    @Test
    void acceptsPaycomWithTheCashboxKey() {
        assertThat(PaymeRules.isAuthorized(basic("Paycom:secret-key"), "secret-key")).isTrue();
    }

    @Test
    void refusesAnythingElse() {
        assertThat(PaymeRules.isAuthorized(basic("Paycom:wrong-key"), "secret-key")).isFalse();
        assertThat(PaymeRules.isAuthorized(basic("Someone:secret-key"), "secret-key")).isFalse();
        assertThat(PaymeRules.isAuthorized("Bearer " + "abc", "secret-key")).isFalse();
        assertThat(PaymeRules.isAuthorized("Basic not-base64!", "secret-key")).isFalse();
        assertThat(PaymeRules.isAuthorized(null, "secret-key")).isFalse();
    }

    /** An unconfigured key must never be satisfied by an empty password. */
    @Test
    void refusesEverythingWhileNoKeyIsConfigured() {
        assertThat(PaymeRules.isAuthorized(basic("Paycom:"), "")).isFalse();
        assertThat(PaymeRules.isAuthorized(basic("Paycom:"), null)).isFalse();
    }

    @Test
    void amountIsInTiyinAndMustMatchExactly() {
        assertThat(PaymeRules.amountMatches(new BigDecimal("500000"), TOPUP)).isTrue();
        assertThat(PaymeRules.amountMatches(new BigDecimal("500000.00"), TOPUP)).isTrue();
        assertThat(PaymeRules.amountMatches(new BigDecimal("5000"), TOPUP)).isFalse();
        assertThat(PaymeRules.amountMatches(new BigDecimal("499999"), TOPUP)).isFalse();
        assertThat(PaymeRules.amountMatches(null, TOPUP)).isFalse();
    }

    @Test
    void aCreatedTransactionTimesOutAfterTwelveHours() {
        Instant twelveHoursAgo = NOW.minusMillis(PaymeRules.TIMEOUT_MILLIS);

        assertThat(PaymeRules.isTimedOut(created(twelveHoursAgo), NOW)).isFalse();
        assertThat(PaymeRules.isTimedOut(created(twelveHoursAgo.minusMillis(1)), NOW)).isTrue();
    }

    /** Only state 1 can time out: a performed transaction stays performed however old it is. */
    @Test
    void aPerformedTransactionNeverTimesOut() {
        PaymentProviderTransaction performed = created(NOW.minusSeconds(86_400)).perform(NOW.minusSeconds(86_000));

        assertThat(PaymeRules.isTimedOut(performed, NOW)).isFalse();
    }

    @Test
    void statesAreNumberedTheWayPaymeNumbersThem() {
        assertThat(PaymeRules.stateCode(ProviderTransactionState.CREATED)).isEqualTo(1);
        assertThat(PaymeRules.stateCode(ProviderTransactionState.PERFORMED)).isEqualTo(2);
        assertThat(PaymeRules.stateCode(ProviderTransactionState.CANCELLED)).isEqualTo(-1);
    }

    private static PaymentProviderTransaction created(Instant createdAt) {
        return new PaymentProviderTransaction(10L, PaymentMethod.PAYME, "63f0a1b2c3d4e5f601234567", 1L,
                "PAY-1A2B3C4D", 500_000, ProviderTransactionState.CREATED, createdAt, createdAt, null, null, null);
    }

    private static String basic(String credentials) {
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
