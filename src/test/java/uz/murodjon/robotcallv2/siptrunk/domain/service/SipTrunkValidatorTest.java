package uz.murodjon.robotcallv2.siptrunk.domain.service;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.ValidationException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SipTrunkValidatorTest {

    @Test
    void endpointNamesAreLettersDigitsUnderscoreAndDash() {
        assertThat(SipTrunkValidator.isValidEndpointName("trunk_1_2")).isTrue();
        assertThat(SipTrunkValidator.isValidEndpointName("trunk-endpoint")).isTrue();
        assertThat(SipTrunkValidator.isValidEndpointName("600")).isTrue();
    }

    @Test
    void endpointNamesThatWouldBreakTheDialStringOrConfigAreRefused() {
        assertThat(SipTrunkValidator.isValidEndpointName("a@b")).isFalse();
        assertThat(SipTrunkValidator.isValidEndpointName("a/b")).isFalse();
        assertThat(SipTrunkValidator.isValidEndpointName("x\r\nAction: Command")).isFalse();
        assertThat(SipTrunkValidator.isValidEndpointName(" trunk")).isFalse();
        assertThat(SipTrunkValidator.isValidEndpointName("")).isFalse();
        assertThat(SipTrunkValidator.isValidEndpointName(null)).isFalse();
        assertThat(SipTrunkValidator.isValidEndpointName("a".repeat(65))).isFalse();
    }

    @Test
    void hostsAreHostnamesOrIpv4() {
        assertThat(SipTrunkValidator.isValidHost("sip.provider.uz")).isTrue();
        assertThat(SipTrunkValidator.isValidHost("10.0.0.5")).isTrue();
        assertThat(SipTrunkValidator.isValidHost("x\n[evil]\ntype=identify")).isFalse();
        assertThat(SipTrunkValidator.isValidHost("host;comment")).isFalse();
        assertThat(SipTrunkValidator.isValidHost(".leading-dot")).isFalse();
    }

    @Test
    void usernamesWithAConfigBreakingCharacterAreRefused() {
        assertThatCode(() -> SipTrunkValidator.requireUsername("998712000002")).doesNotThrowAnyException();
        assertThatThrownBy(() -> SipTrunkValidator.requireUsername("user\npassword = x"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> SipTrunkValidator.requireUsername("user@host"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void passwordsMayHoldAnyPrintableCharacterButNoLineBreak() {
        assertThat(SipTrunkValidator.isValidPassword("p@ss;w0rd#!")).isTrue();
        assertThat(SipTrunkValidator.isValidPassword("secret\n[evil]")).isFalse();
        assertThat(SipTrunkValidator.isValidPassword("tab\there")).isFalse();
    }

    @Test
    void onlyKnownCodecsAreAccepted() {
        assertThatCode(() -> SipTrunkValidator.requireSupportedCodecs(List.of("G722", " opus ", "ulaw")))
                .doesNotThrowAnyException();
        assertThatCode(() -> SipTrunkValidator.requireSupportedCodecs(null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> SipTrunkValidator.requireSupportedCodecs(List.of("ulaw\n[evil]")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aBlankCallerIdFallsBackToTheGlobalOne() {
        assertThatCode(() -> SipTrunkValidator.requireValidCallerId(null)).doesNotThrowAnyException();
        assertThatCode(() -> SipTrunkValidator.requireValidCallerId(" ")).doesNotThrowAnyException();
        assertThatCode(() -> SipTrunkValidator.requireValidCallerId("998712000001")).doesNotThrowAnyException();
        assertThatThrownBy(() -> SipTrunkValidator.requireValidCallerId("Bank <998712000001>"))
                .isInstanceOf(ValidationException.class);
    }
}
