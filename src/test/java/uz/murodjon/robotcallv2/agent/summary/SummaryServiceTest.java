package uz.murodjon.robotcallv2.agent.summary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SummaryServiceTest {

    @Test
    void acceptsUzbekAndInternationalPhoneFormatsButNotExtensions() {
        assertThat(SummaryService.coerce("90 123 45 67", "phone")).isEqualTo("90 123 45 67");
        assertThat(SummaryService.coerce("+998 90 123 45 67", "phone")).isEqualTo("+998 90 123 45 67");
        assertThat(SummaryService.coerce("1234", "phone")).isNull();
        assertThat(SummaryService.coerce("++998901234567", "phone")).isNull();
    }

    @Test
    void acceptsCommonEmailAddressesAndRejectsMalformedOnes() {
        assertThat(SummaryService.coerce("ali+sales@example.uz", "email")).isEqualTo("ali+sales@example.uz");
        assertThat(SummaryService.coerce("ali..vali@example.uz", "email")).isNull();
        assertThat(SummaryService.coerce("ali@example", "email")).isNull();
        assertThat(SummaryService.coerce("ali @example.uz", "email")).isNull();
    }
}
