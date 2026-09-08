package uz.murodjon.robotcallv2.apikey.domain.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeySecretTest {

    @Test
    void readsBackThePrefixItGenerated() {
        String prefix = ApiKeySecret.generatePrefix();
        String key = ApiKeySecret.generateKey(prefix);

        assertThat(ApiKeySecret.readPrefix(key)).isEqualTo(prefix);
    }

    /**
     * The secret half is base64url and may contain an underscore, so the prefix has to be
     * cut at a known position rather than at the last separator in the string.
     */
    @Test
    void readsThePrefixEvenWhenTheSecretContainsUnderscores() {
        String prefix = "rc_live_0123456789ab";

        assertThat(ApiKeySecret.readPrefix(prefix + "_aa_bb_cc")).isEqualTo(prefix);
    }

    @Test
    void refusesAnythingNotShapedLikeOneOfOurs() {
        assertThat(ApiKeySecret.readPrefix(null)).isNull();
        assertThat(ApiKeySecret.readPrefix("")).isNull();
        assertThat(ApiKeySecret.readPrefix("Bearer abcdef")).isNull();
        assertThat(ApiKeySecret.readPrefix("rc_live_0123456789ab")).isNull();   // prefix only, no secret
        assertThat(ApiKeySecret.readPrefix("rc_live_short_secret")).isNull();
    }

    @Test
    void matchesOnlyTheKeyItHashed() {
        String key = ApiKeySecret.generateKey(ApiKeySecret.generatePrefix());
        String hash = ApiKeySecret.hash(key);

        assertThat(ApiKeySecret.matches(key, hash)).isTrue();
        assertThat(ApiKeySecret.matches(key + "x", hash)).isFalse();
        assertThat(ApiKeySecret.matches(null, hash)).isFalse();
        assertThat(ApiKeySecret.matches(key, null)).isFalse();
    }

    @Test
    void generatesADifferentKeyEveryTime() {
        String prefix = ApiKeySecret.generatePrefix();

        assertThat(ApiKeySecret.generateKey(prefix)).isNotEqualTo(ApiKeySecret.generateKey(prefix));
        assertThat(ApiKeySecret.generatePrefix()).isNotEqualTo(ApiKeySecret.generatePrefix());
    }
}
