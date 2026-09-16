package uz.murodjon.robotcallv2.agent.rtp;

import org.junit.jupiter.api.Test;
import uz.murodjon.robotcallv2.shared.exception.ExternalServiceException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RtpPortAllocatorTest {

    private static RtpPortAllocator allocator(int start, int end) {
        return new RtpPortAllocator(new RtpProperties("127.0.0.1", start, end, "recordings", null, 1, null, null, 0, 0));
    }

    /** The previous call's externalMedia leg may still be sending to a port it just gave back. */
    @Test
    void aReleasedPortIsHandedOutLast() {
        RtpPortAllocator ports = allocator(10000, 10004);
        int first = ports.allocate();
        ports.release(first);

        assertThat(ports.allocate()).isEqualTo(10002);
        assertThat(ports.allocate()).isEqualTo(10004);
        assertThat(ports.allocate()).isEqualTo(first);
    }

    @Test
    void runningOutIsAnExternalServiceFailure() {
        RtpPortAllocator ports = allocator(10000, 10000);
        ports.allocate();

        assertThatThrownBy(ports::allocate).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void aDoubleReleaseDoesNotHandTheSamePortOutTwice() {
        RtpPortAllocator ports = allocator(10000, 10002);
        int port = ports.allocate();
        ports.release(port);
        ports.release(port);

        assertThat(ports.allocate()).isEqualTo(10002);
        assertThat(ports.allocate()).isEqualTo(10000);
        assertThatThrownBy(ports::allocate).isInstanceOf(ExternalServiceException.class);
    }
}
