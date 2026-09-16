package uz.murodjon.robotcallv2.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** X-Forwarded-For counts only behind a proxy the config says overwrites it — else it is caller-controlled. */
class ClientAddressArgumentResolverTest {

    @Test
    void ignoresForwardedForUnlessTrusted() {
        MockHttpServletRequest request = request("203.0.113.9, 10.0.0.1");

        assertThat(ClientAddressArgumentResolver.resolve(request, false)).isEqualTo("10.0.0.5");
    }

    @Test
    void takesTheFirstForwardedHopWhenTrusted() {
        MockHttpServletRequest request = request("203.0.113.9, 10.0.0.1");

        assertThat(ClientAddressArgumentResolver.resolve(request, true)).isEqualTo("203.0.113.9");
    }

    private static MockHttpServletRequest request(String forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.5");
        request.addHeader("X-Forwarded-For", forwardedFor);
        return request;
    }
}
