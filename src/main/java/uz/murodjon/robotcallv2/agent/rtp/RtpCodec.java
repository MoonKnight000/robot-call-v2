package uz.murodjon.robotcallv2.agent.rtp;

import uz.murodjon.robotcallv2.agent.codec.G711Codec;

/**
 * Codec of the media leg between this app and Asterisk. Both are G.711 at 8 kHz, so
 * the choice changes no sample rate anywhere in the pipeline — it exists so the leg
 * can match the trunk's codec and spare Asterisk a transcode per call.
 */
public enum RtpCodec {

    /** PCMU, RTP payload type 0. */
    ULAW(0, "ulaw", (byte) 0xFF) {
        @Override
        public byte encode(short pcm) {
            return G711Codec.pcmToUlaw(pcm);
        }
    },

    /** PCMA, RTP payload type 8. */
    ALAW(8, "alaw", (byte) 0xD5) {
        @Override
        public byte encode(short pcm) {
            return G711Codec.pcmToAlaw(pcm);
        }
    };

    private final int payloadType;
    private final String asteriskFormat;
    private final byte silence;

    RtpCodec(int payloadType, String asteriskFormat, byte silence) {
        this.payloadType = payloadType;
        this.asteriskFormat = asteriskFormat;
        this.silence = silence;
    }

    public int payloadType() {
        return payloadType;
    }

    /** The format name ARI's {@code externalMedia} takes. */
    public String asteriskFormat() {
        return asteriskFormat;
    }

    /** The encoded byte for a zero sample. */
    public byte silence() {
        return silence;
    }

    public abstract byte encode(short pcm);
}
