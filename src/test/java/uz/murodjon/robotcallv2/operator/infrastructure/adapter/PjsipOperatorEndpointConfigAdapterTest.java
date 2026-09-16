package uz.murodjon.robotcallv2.operator.infrastructure.adapter;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.operator.domain.entity.OperatorSipEndpoint;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PjsipOperatorEndpointConfigAdapterTest {

    @Test
    void aBrowserSoftphoneIsAWebrtcEndpointThatCanOnlyReachTheApplication() {
        String config = PjsipOperatorEndpointConfigAdapter.render(List.of(
                new OperatorSipEndpoint(12, 1, "op-12", "s3cret", true, "Dilnoza", "101")));

        assertThat(config)
                .contains("[op-12-auth]", "username = op-12", "password = s3cret")
                .contains("context = from-operators")
                .contains("callerid = \"Dilnoza\" <101>")
                .contains("webrtc = yes")
                .doesNotContain("transport = transport-lan");
    }

    @Test
    void aDeskPhoneUsesTheLanTransport() {
        String config = PjsipOperatorEndpointConfigAdapter.render(List.of(
                new OperatorSipEndpoint(13, 1, "op-13", "x", false, "Aziz", null)));

        assertThat(config)
                .contains("transport = transport-lan", "rtp_symmetric = yes")
                .contains("callerid = \"Aziz\" <op-13>")
                .doesNotContain("webrtc = yes");
    }

    @Test
    void aNameCannotBreakOutOfItsLine() {
        String config = PjsipOperatorEndpointConfigAdapter.render(List.of(
                new OperatorSipEndpoint(14, 1, "op-14", "x", true, "Evil\"\ncontext = from-trunk", "102")));

        assertThat(config.lines().filter(line -> line.startsWith("callerid =")).toList())
                .containsExactly("callerid = \"Evil  context = from-trunk\" <102>");
        assertThat(config.lines().filter(line -> line.startsWith("context =")).toList())
                .containsExactly("context = from-operators");
    }
}
