package uz.murodjon.robotcallv2.agent.codec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class G711CodecTest {

    @Test
    void ulawSilenceDecodesNearZero() {
        // 0xFF is µ-law encoded silence; it decodes to 0.
        assertEquals(0, G711Codec.ulawToPcm((byte) 0xFF));
    }

    @Test
    void ulawSignBitSeparatesPositiveAndNegative() {
        // µ-law bytes with the high bit clear (0x00) decode negative; set (0x80) positive.
        assertTrue(G711Codec.ulawToPcm((byte) 0x00) < 0);
        assertTrue(G711Codec.ulawToPcm((byte) 0x80) > 0);
    }

    @Test
    void alawSilenceDecodesNearZero() {
        // 0xD5 is A-law encoded silence.
        assertEquals(8, G711Codec.alawToPcm((byte) 0xD5));
    }

    @Test
    void ulawFullScaleIsLoud() {
        // 0x00 (inverts to 0xFF) is maximum negative amplitude.
        short sample = G711Codec.ulawToPcm((byte) 0x00);
        assertTrue(sample < -30000, "expected near full-scale negative, got " + sample);
    }

    @Test
    void bulkDecodeMatchesSingleSample() {
        byte[] in = {(byte) 0xFF, (byte) 0x7F, (byte) 0x00, 0x12};
        short[] out = new short[in.length];
        G711Codec.ulawToPcm(in, in.length, out);
        for (int i = 0; i < in.length; i++) {
            assertEquals(G711Codec.ulawToPcm(in[i]), out[i]);
        }
    }

    @Test
    void silenceEncodesToStandardBytes() {
        assertEquals((byte) 0xFF, G711Codec.pcmToUlaw((short) 0));
        assertEquals((byte) 0xD5, G711Codec.pcmToAlaw((short) 0));
    }

    @Test
    void ulawRoundTripIsClose() {
        for (short original : new short[]{1000, -5000, 12000, -20000, 200, -200}) {
            short decoded = G711Codec.ulawToPcm(G711Codec.pcmToUlaw(original));
            assertTrue(Math.signum(decoded) == Math.signum(original) || decoded == 0,
                    "sign flipped for " + original + " -> " + decoded);
            int tolerance = Math.abs(original) / 10 + 64; // G.711 is lossy but bounded
            assertTrue(Math.abs(decoded - original) <= tolerance,
                    "round-trip too far: " + original + " -> " + decoded);
        }
    }

    @Test
    void bulkEncodeMatchesSingleSample() {
        short[] in = {0, 1000, -1000, 20000};
        byte[] out = new byte[in.length];
        G711Codec.pcmToUlaw(in, in.length, out);
        for (int i = 0; i < in.length; i++) {
            assertEquals(G711Codec.pcmToUlaw(in[i]), out[i]);
        }
    }
}
