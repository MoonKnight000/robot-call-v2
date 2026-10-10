package uz.murodjon.robotcallv2.dialer.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.dialer.domain.entity.ScrubResult;
import uz.murodjon.robotcallv2.dialer.domain.enums.ScrubStatus;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberScrubberTest {

    private PhoneNumberScrubber scrubber;

    @BeforeEach
    void setUp() {
        scrubber = new PhoneNumberScrubber();
    }

    @Test
    void acceptsValidUzbekMobileNumbers() {
        ScrubResult beeline = scrubber.scrub("+998901234567");
        assertThat(beeline.valid()).isTrue();
        assertThat(beeline.status()).isEqualTo(ScrubStatus.VALID);
        assertThat(beeline.operatorOrRegion()).isEqualTo("Beeline");

        ScrubResult ucell = scrubber.scrub("998931234567");
        assertThat(ucell.valid()).isTrue();
        assertThat(ucell.operatorOrRegion()).isEqualTo("Ucell");

        ScrubResult humans = scrubber.scrub("331234567");
        assertThat(humans.valid()).isTrue();
        assertThat(humans.operatorOrRegion()).isEqualTo("Humans");

        ScrubResult mobiuz = scrubber.scrub("+998881234567");
        assertThat(mobiuz.valid()).isTrue();
        assertThat(mobiuz.operatorOrRegion()).isEqualTo("Mobiuz");
    }

    @Test
    void acceptsValidUzbekLandlineNumbers() {
        ScrubResult tashkent = scrubber.scrub("+998712001122");
        assertThat(tashkent.valid()).isTrue();
        assertThat(tashkent.operatorOrRegion()).isEqualTo("Tashkent City");

        ScrubResult samarkand = scrubber.scrub("+998661234567");
        assertThat(samarkand.valid()).isTrue();
        assertThat(samarkand.operatorOrRegion()).isEqualTo("Samarkand");
    }

    @Test
    void rejectsUnallocatedUzbekPrefixes() {
        ScrubResult unallocated = scrubber.scrub("+998011234567");
        assertThat(unallocated.valid()).isFalse();
        assertThat(unallocated.status()).isEqualTo(ScrubStatus.UNALLOCATED_PREFIX);
        assertThat(unallocated.reason()).contains("Unallocated Uzbekistan prefix");

        ScrubResult invalidPrefix = scrubber.scrub("+998421234567");
        assertThat(invalidPrefix.valid()).isFalse();
        assertThat(invalidPrefix.status()).isEqualTo(ScrubStatus.UNALLOCATED_PREFIX);
    }

    @Test
    void rejectsMalformedOrBlankNumbers() {
        assertThat(scrubber.scrub("").valid()).isFalse();
        assertThat(scrubber.scrub("   ").valid()).isFalse();
        assertThat(scrubber.scrub("abc").valid()).isFalse();
        assertThat(scrubber.scrub("+9989012").valid()).isFalse(); // Too short
    }

    @Test
    void acceptsInternalTestExtensions() {
        ScrubResult ext = scrubber.scrub("600");
        assertThat(ext.valid()).isTrue();
        assertThat(ext.operatorOrRegion()).isEqualTo("Internal Test Extension");

        ScrubResult ext4 = scrubber.scrub("6001");
        assertThat(ext4.valid()).isTrue();
    }

    @Test
    void acceptsValidInternationalNumbers() {
        ScrubResult intl = scrubber.scrub("+14155552671");
        assertThat(intl.valid()).isTrue();
        assertThat(intl.operatorOrRegion()).isEqualTo("International");
    }
}
