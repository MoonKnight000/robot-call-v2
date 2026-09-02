package uz.murodjon.robotcallv2.agent.rtp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtpPacketTest {

    @Test
    void parsesBasicHeaderAndPayload() {
        byte[] data = {
                (byte) 0x80,             // version 2, no padding/extension, 0 CSRC
                0x00,                     // marker 0, payload type 0 (PCMU)
                0x01, 0x02,               // sequence number = 0x0102
                0x0A, 0x0B, 0x0C, 0x0D,   // timestamp
                0x11, 0x22, 0x33, 0x44,   // SSRC
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC // payload
        };
        RtpPacket p = RtpPacket.parse(data, data.length);

        assertEquals(0, p.payloadType());
        assertEquals(0x0102, p.sequenceNumber());
        assertEquals(0x0A0B0C0DL, p.timestamp());
        assertEquals(0x11223344L, p.ssrc());
        assertFalse(p.marker());
        assertArrayEquals(new byte[]{(byte) 0xAA, (byte) 0xBB, (byte) 0xCC}, p.payload());
    }

    @Test
    void readsMarkerBitAndPayloadType() {
        byte[] data = {
                (byte) 0x80,
                (byte) 0x88,             // marker 1, payload type 8 (PCMA)
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x01
        };
        RtpPacket p = RtpPacket.parse(data, data.length);
        assertTrue(p.marker());
        assertEquals(8, p.payloadType());
    }

    @Test
    void stripsPadding() {
        byte[] data = {
                (byte) 0xA0,             // version 2, padding bit set
                0x00,
                0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00,
                (byte) 0xAA,             // real payload
                0x00, 0x02               // 2 padding bytes, last byte = pad count
        };
        RtpPacket p = RtpPacket.parse(data, data.length);
        assertArrayEquals(new byte[]{(byte) 0xAA}, p.payload());
    }

    @Test
    void rejectsTooShortBuffer() {
        assertThrows(IllegalArgumentException.class, () -> RtpPacket.parse(new byte[8], 8));
    }
}
