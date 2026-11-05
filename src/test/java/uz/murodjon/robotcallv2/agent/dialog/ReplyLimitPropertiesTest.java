package uz.murodjon.robotcallv2.agent.dialog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyLimitPropertiesTest {

    @Test
    void thinkingModelGetsTheReserveOnTop() {
        assertThat(new ReplyLimitProperties(70, 768).findCap(true)).isEqualTo(838);
    }

    @Test
    void modelThatDoesNotThinkGetsTheBareReplyCap() {
        assertThat(new ReplyLimitProperties(70, 768).findCap(false)).isEqualTo(70);
    }

    @Test
    void zeroLeavesTheVendorDefault() {
        assertThat(new ReplyLimitProperties(0, 768).findCap(true)).isNull();
    }
}
