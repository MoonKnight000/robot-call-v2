package uz.murodjon.robotcallv2.billing.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Trial credit 20 000 in every case; trial money is spent before the company's own. */
class TrialCreditRulesTest {

    private static final long CREDIT = 20_000;

    @Test
    void takesBackAllOfAnUntouchedTrial() {
        assertThat(TrialCreditRules.findUnspentUzs(CREDIT, 20_000, 0, 0)).isEqualTo(20_000);
    }

    @Test
    void takesBackOnlyWhatWasNotSpent() {
        // spent 7 000 of the trial
        assertThat(TrialCreditRules.findUnspentUzs(CREDIT, 13_000, 0, 0)).isEqualTo(13_000);
    }

    @Test
    void takesNothingWhenTheTrialWasSpentBeforeTheCompanyPaid() {
        // 20 000 trial + 100 000 paid - 30 000 spent = 90 000, all of it the company's own
        assertThat(TrialCreditRules.findUnspentUzs(CREDIT, 90_000, 0, 100_000)).isZero();
    }

    @Test
    void takesBackTheTrialButNotThePaymentWhenNothingWasSpent() {
        assertThat(TrialCreditRules.findUnspentUzs(CREDIT, 120_000, 0, 100_000)).isEqualTo(20_000);
    }

    @Test
    void neverTakesMoneyHeldForARunningCall() {
        // 15 000 unspent, but 5 000 of it is reserved for a call in the air
        assertThat(TrialCreditRules.findUnspentUzs(CREDIT, 15_000, 5_000, 0)).isEqualTo(10_000);
    }

    @Test
    void neverGoesNegative() {
        assertThat(TrialCreditRules.findUnspentUzs(CREDIT, -2_000, 0, 0)).isZero();
    }
}
