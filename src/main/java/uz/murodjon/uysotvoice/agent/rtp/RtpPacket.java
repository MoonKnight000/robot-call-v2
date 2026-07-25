package uz.murodjon.uysotvoice.agent.rtp;

/**
 * A parsed RTP packet (RFC 3550). Only the fields needed by the pipeline are
 * exposed. Parsing is a pure function — unit tested without any I/O.
 *
 * @param payloadType    RTP payload type (0 = PCMU/µ-law, 8 = PCMA/A-law)
 * @param sequenceNumber 16-bit sequence number
 * @param timestamp      32-bit media timestamp
 * @param ssrc           synchronization source identifier
 * @param marker         marker bit
 * @param payload        the media payload (header, CSRCs, extension and padding stripped)
 */
public record RtpPacket(
        int payloadType,
        int sequenceNumber,
        long timestamp,
        long ssrc,
        boolean marker,
        byte[] payload
) {

    private static final int MIN_HEADER = 12;

    /**
     * Parse the first {@code length} bytes of {@code data} as an RTP packet.
     *
     * @throws IllegalArgumentException if the buffer is too short or malformed
     */
    public static RtpPacket parse(byte[] data, int length) {
        if (length < MIN_HEADER) {
            throw new IllegalArgumentException("RTP packet too short: " + length);
        }
        int b0 = data[0] & 0xFF;
        boolean padding = (b0 & 0x20) != 0;
        boolean extension = (b0 & 0x10) != 0;
        int csrcCount = b0 & 0x0F;

        int b1 = data[1] & 0xFF;
        boolean marker = (b1 & 0x80) != 0;
        int payloadType = b1 & 0x7F;

        int seq = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
        long timestamp = ((long) (data[4] & 0xFF) << 24) | ((data[5] & 0xFF) << 16)
                | ((data[6] & 0xFF) << 8) | (data[7] & 0xFF);
        long ssrc = ((long) (data[8] & 0xFF) << 24) | ((data[9] & 0xFF) << 16)
                | ((data[10] & 0xFF) << 8) | (data[11] & 0xFF);

        int headerLen = MIN_HEADER + csrcCount * 4;
        if (extension) {
            if (length < headerLen + 4) {
                throw new IllegalArgumentException("truncated RTP extension header");
            }
            int extWords = ((data[headerLen + 2] & 0xFF) << 8) | (data[headerLen + 3] & 0xFF);
            headerLen += 4 + extWords * 4;
        }

        int payloadLen = length - headerLen;
        if (padding && length > 0) {
            payloadLen -= data[length - 1] & 0xFF;
        }
        if (payloadLen < 0 || headerLen > length) {
            throw new IllegalArgumentException("malformed RTP packet: header " + headerLen + " > length " + length);
        }

        byte[] payload = new byte[payloadLen];
        System.arraycopy(data, headerLen, payload, 0, payloadLen);
        return new RtpPacket(payloadType, seq, timestamp, ssrc, marker, payload);
    }

    /**
     * Serialize an outgoing RTP packet: a 12-byte header (version 2, no padding,
     * no extension, no CSRC) followed by {@code payload}.
     */
    public static byte[] toBytes(int payloadType, int sequenceNumber, long timestamp,
                                 long ssrc, boolean marker, byte[] payload) {
        byte[] out = new byte[MIN_HEADER + payload.length];
        out[0] = (byte) 0x80; // version 2
        out[1] = (byte) ((marker ? 0x80 : 0x00) | (payloadType & 0x7F));
        out[2] = (byte) ((sequenceNumber >> 8) & 0xFF);
        out[3] = (byte) (sequenceNumber & 0xFF);
        out[4] = (byte) ((timestamp >> 24) & 0xFF);
        out[5] = (byte) ((timestamp >> 16) & 0xFF);
        out[6] = (byte) ((timestamp >> 8) & 0xFF);
        out[7] = (byte) (timestamp & 0xFF);
        out[8] = (byte) ((ssrc >> 24) & 0xFF);
        out[9] = (byte) ((ssrc >> 16) & 0xFF);
        out[10] = (byte) ((ssrc >> 8) & 0xFF);
        out[11] = (byte) (ssrc & 0xFF);
        System.arraycopy(payload, 0, out, MIN_HEADER, payload.length);
        return out;
    }
}
