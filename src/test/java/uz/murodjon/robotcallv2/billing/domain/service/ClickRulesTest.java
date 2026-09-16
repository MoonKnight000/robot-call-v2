package uz.murodjon.robotcallv2.billing.domain.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.billing.domain.entity.PaymentTopup;
import uz.murodjon.robotcallv2.billing.domain.enums.PaymentMethod;
import uz.murodjon.robotcallv2.billing.domain.enums.TopupStatus;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The expected signs were computed outside Java ({@code printf '%s' '<payload>' | md5sum}),
 * so a mistake in how the payload is put together cannot also be baked into the answer.
 */
class ClickRulesTest {

    private static final String SECRET = "s3cr3t";

    /** md5("200381s3cr3tPAY-1A2B3C4D1000.0002026-09-14 10:00:00") */
    private static final String PREPARE_SIGN = "0f7e56f0f34199e7642e144a1ccc7e28";

    /** md5("200381s3cr3tPAY-1A2B3C4D71000.0012026-09-14 10:00:05") */
    private static final String COMPLETE_SIGN = "6716859c5a878f371b54111ac9d7ddb2";

    @Test
    void signsPrepareWithoutMerchantPrepareId() {
        assertThat(ClickRules.sign("2003", "81", SECRET, "PAY-1A2B3C4D", null, "1000.00", "0",
                "2026-09-14 10:00:00")).isEqualTo(PREPARE_SIGN);
    }

    @Test
    void signsCompleteWithMerchantPrepareIdRightAfterMerchantTransId() {
        assertThat(ClickRules.sign("2003", "81", SECRET, "PAY-1A2B3C4D", "7", "1000.00", "1",
                "2026-09-14 10:00:05")).isEqualTo(COMPLETE_SIGN);
    }

    /** "1000" and "1000.00" are the same money but not the same sign: the raw string is signed. */
    @Test
    void signsTheAmountExactlyAsClickSentIt() {
        assertThat(ClickRules.sign("2003", "81", SECRET, "PAY-1A2B3C4D", null, "1000", "0",
                "2026-09-14 10:00:00")).isNotEqualTo(PREPARE_SIGN);
    }

    @Test
    void signComparisonIgnoresHexCaseAndNothingElse() {
        assertThat(ClickRules.signMatches(PREPARE_SIGN, PREPARE_SIGN.toUpperCase())).isTrue();
        assertThat(ClickRules.signMatches(PREPARE_SIGN, COMPLETE_SIGN)).isFalse();
        assertThat(ClickRules.signMatches(PREPARE_SIGN, "")).isFalse();
        assertThat(ClickRules.signMatches(PREPARE_SIGN, null)).isFalse();
    }

    @Test
    void amountIsInUzsAndMayCarryDecimals() {
        PaymentTopup topup = new PaymentTopup(1L, "PAY-1A2B3C4D", 7L, null, 1_000, PaymentMethod.CLICK,
                TopupStatus.PENDING, null, Instant.now(), null);

        assertThat(ClickRules.amountMatches(new BigDecimal("1000.00"), topup)).isTrue();
        assertThat(ClickRules.amountMatches(new BigDecimal("1000"), topup)).isTrue();
        assertThat(ClickRules.amountMatches(new BigDecimal("1000.01"), topup)).isFalse();
        assertThat(ClickRules.amountMatches(new BigDecimal("100000"), topup)).isFalse();
        assertThat(ClickRules.amountMatches(null, topup)).isFalse();
    }
}
