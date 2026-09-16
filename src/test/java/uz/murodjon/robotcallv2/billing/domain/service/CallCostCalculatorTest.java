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
            300,     // realtime engine: 300 UZS/minute
            180,     // telephony: 180 UZS/minute
            120,     // platform fee: 120 UZS/minute
            0);

    @Test
    void chargesNothingForACallThatConsumedNothing() {
        assertThat(CallCostCalculator.calculate(CallUsage.NONE, PLAIN)).isEqualTo(CallCostBreakdown.FREE);
    }

    @Test
    void theMinuteFloorIsTheLineTheEngineAndTheFee() {
        // Cascade: telephony 180 + stt 60 + platform 120. Realtime: telephony 180 + engine 300 + platform 120.
        assertThat(CallCostCalculator.minimumPerMinuteUzs(PLAIN, false)).isEqualTo(360);
        assertThat(CallCostCalculator.minimumPerMinuteUzs(PLAIN, true)).isEqualTo(600);
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
        BillingRates marked = new BillingRates(PLAIN.llm(), 60, 50, 300, 180, 120, 2000); // +20%

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
                cost.llmUzs() + cost.sttUzs() + cost.ttsUzs() + cost.realtimeUzs()
                        + cost.telephonyUzs() + cost.platformFeeUzs());
    }

    /**
     * A realtime call is priced on the engine's minutes; the audio tokens it reported are
     * recorded but must not also be billed at the text-LLM rate, or one call would be
     * charged twice for the same speech.
     */
    @Test
    void pricesARealtimeCallOnItsMinutesNotItsTokens() {
        // 120s = 2 minutes: realtime 2 * 300 = 600, telephony 360, platform 240.
        CallCostBreakdown cost = CallCostCalculator.calculate(
                new CallUsage(120, 400_000, 50_000, 10_000, 0, 120), PLAIN);

        assertThat(cost.realtimeUzs()).isEqualTo(600);
        assertThat(cost.llmUzs()).isZero();
        assertThat(cost.sttUzs()).isZero();
        assertThat(cost.ttsUzs()).isZero();
        assertThat(cost.telephonyUzs()).isEqualTo(360);
        assertThat(cost.platformFeeUzs()).isEqualTo(240);
        assertThat(cost.totalUzs()).isEqualTo(600 + 360 + 240);
    }

    @Test
    void treatsMissingRatesAsFree() {
        assertThat(CallCostCalculator.calculate(new CallUsage(60, 1, 1, 0, 1), null))
                .isEqualTo(CallCostBreakdown.FREE);
    }
}
