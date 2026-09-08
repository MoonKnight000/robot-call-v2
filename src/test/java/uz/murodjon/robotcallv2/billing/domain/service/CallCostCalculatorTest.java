package uz.murodjon.robotcallv2.billing.domain.service;

import org.junit.jupiter.api.Test;

import uz.murodjon.robotcallv2.billing.domain.entity.BillingLlmRates;
import uz.murodjon.robotcallv2.billing.domain.entity.BillingRates;
import uz.murodjon.robotcallv2.billing.domain.entity.CallCostBreakdown;
import uz.murodjon.robotcallv2.billing.domain.entity.CallUsage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This is the arithmetic a company disputes an invoice over, so the numbers below are
 * worked out by hand in the comments rather than read back from the code.
 */
class CallCostCalculatorTest {

    /** Round prices and no markup, so each assertion is one multiplication. */
    private static final BillingRates PLAIN = new BillingRates(
            new BillingLlmRates(1000, 10_000, 100),
            60,      // stt: 60 UZS/minute
            50,      // tts: 50 UZS per 1k chars
            180,     // telephony: 180 UZS/minute
            120,     // platform fee: 120 UZS/minute
            0);

    @Test
    void chargesNothingForACallThatConsumedNothing() {
        assertThat(CallCostCalculator.calculate(CallUsage.NONE, PLAIN)).isEqualTo(CallCostBreakdown.FREE);
    }

    @Test
    void pricesAMinuteOfSpeechAndTheLine() {
        // 60s = 1 minute: stt 60, telephony 180, platform 120. 2000 chars = 2 * 50 = 100.
        CallCostBreakdown cost = CallCostCalculator.calculate(new CallUsage(60, 0, 0, 0, 2000), PLAIN);

        assertThat(cost.sttUzs()).isEqualTo(60);
        assertThat(cost.telephonyUzs()).isEqualTo(180);
        assertThat(cost.platformFeeUzs()).isEqualTo(120);
        assertThat(cost.ttsUzs()).isEqualTo(100);
        assertThat(cost.totalUzs()).isEqualTo(460);
    }

    /**
     * The cached share of the prompt is billed at the cached rate, not the full one —
     * on a voice call the stable prefix is re-sent every turn, so getting this wrong
     * charges a company several times over for one system prompt.
     */
    @Test
    void billsCachedPromptTokensAtTheCachedRate() {
        // 1M prompt of which 900k cached: 100k @ 1000/M = 100, 900k @ 100/M = 90.
        // 200k completion @ 10000/M = 2000.
        CallCostBreakdown cost = CallCostCalculator.calculate(
                new CallUsage(0, 1_000_000, 200_000, 900_000, 0), PLAIN);

        assertThat(cost.llmUzs()).isEqualTo(100 + 90 + 2000);
    }

    @Test
    void marksUpProviderCostsButNotThePlatformFee() {
        BillingRates marked = new BillingRates(PLAIN.llm(), 60, 50, 180, 120, 2000); // +20%

        CallCostBreakdown cost = CallCostCalculator.calculate(new CallUsage(60, 0, 0, 0, 2000), marked);

        assertThat(cost.sttUzs()).isEqualTo(72);          // 60 * 1.2
        assertThat(cost.telephonyUzs()).isEqualTo(216);   // 180 * 1.2
        assertThat(cost.ttsUzs()).isEqualTo(120);         // 100 * 1.2
        assertThat(cost.platformFeeUzs()).isEqualTo(120); // untouched
        assertThat(cost.totalUzs()).isEqualTo(72 + 216 + 120 + 120);
    }

    /** The total has to be the sum of the lines, or an invoice does not add up by hand. */
    @Test
    void totalIsTheSumOfItsLines() {
        CallCostBreakdown cost = CallCostCalculator.calculate(
                new CallUsage(137, 24_310, 3_820, 19_004, 1_733), PLAIN);

        assertThat(cost.totalUzs()).isEqualTo(
                cost.llmUzs() + cost.sttUzs() + cost.ttsUzs() + cost.telephonyUzs() + cost.platformFeeUzs());
    }

    @Test
    void treatsMissingRatesAsFree() {
        assertThat(CallCostCalculator.calculate(new CallUsage(60, 1, 1, 0, 1), null))
                .isEqualTo(CallCostBreakdown.FREE);
    }
}
