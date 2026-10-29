package uz.murodjon.robotcallv2.phonenumber.infrastructure.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.murodjon.robotcallv2.phonenumber.application.port.output.SipProbePort;
import uz.murodjon.robotcallv2.phonenumber.domain.entity.SipProbeResult;
import uz.murodjon.robotcallv2.shared.util.PublicUrlGuard;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** A TCP connect, or for UDP a single SIP OPTIONS datagram, to an address {@link PublicUrlGuard} has vetted. */
@Component
public class SocketSipProbeAdapter implements SipProbePort {

    private static final Logger log = LoggerFactory.getLogger(SocketSipProbeAdapter.class);
    private static final int TIMEOUT_MS = 3000;

    @Override
    public SipProbeResult probe(String host, int port, String transport) {
        long start = System.currentTimeMillis();
        try {
            // The host is the tenant's: connect only to the address judged public here (SSRF).
            InetSocketAddress address = new InetSocketAddress(PublicUrlGuard.resolvePublic(host)[0], port);
            if ("UDP".equals(transport)) {
                sendOptions(address, host, port);
                return new SipProbeResult(true, System.currentTimeMillis() - start, "REACHABLE",
                        "UDP packet transmitted successfully to " + host + ":" + port);
            }
            try (Socket socket = new Socket()) {
                socket.connect(address, TIMEOUT_MS);
            }
            return new SipProbeResult(true, System.currentTimeMillis() - start, "CONNECTED",
                    "TCP connection established with " + host + ":" + port);
        } catch (Exception e) {
            log.warn("SIP connection test failed to {}:{}: {}", host, port, e.getMessage());
            return new SipProbeResult(false, System.currentTimeMillis() - start, "FAILED",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private static void sendOptions(InetSocketAddress address, String host, int port) throws Exception {
        String options = "OPTIONS sip:" + host + ":" + port + " SIP/2.0\r\n"
                + "Via: SIP/2.0/UDP 127.0.0.1:5060;branch=z9hG4bKtest\r\n"
                + "Max-Forwards: 70\r\n"
                + "From: <sip:test@127.0.0.1>;tag=ping\r\n"
                + "To: <sip:" + host + ">\r\n"
                + "Call-ID: ping" + System.currentTimeMillis() + "@127.0.0.1\r\n"
                + "CSeq: 1 OPTIONS\r\n"
                + "Content-Length: 0\r\n\r\n";
        byte[] data = options.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            socket.send(new DatagramPacket(data, data.length, address));
        }
    }
}
