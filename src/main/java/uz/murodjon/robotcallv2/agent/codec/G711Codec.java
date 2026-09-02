package uz.murodjon.robotcallv2.agent.codec;

/**
 * G.711 codec: µ-law (PCMU, RTP payload type 0) and A-law (PCMA, type 8)
 * to/from 16-bit linear PCM. Decode (Stage 3, incoming audio) and encode
 * (Stage 4, outgoing audio) both follow the ITU-T / Sun reference
 * implementation (g711.c). Pure functions — unit tested without any I/O.
 */
public final class G711Codec {

    private static final int SIGN_BIT = 0x80;
    private static final int QUANT_MASK = 0x0F;
    private static final int SEG_MASK = 0x70;
    private static final int SEG_SHIFT = 4;
    private static final int BIAS = 0x84;
    private static final int CLIP = 8159;

    private static final int[] SEG_UEND = {0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF, 0x1FFF};
    private static final int[] SEG_AEND = {0x1F, 0x3F, 0x7F, 0xFF, 0x1FF, 0x3FF, 0x7FF, 0xFFF};

    private G711Codec() {
    }

    /** Decode a single µ-law byte to a 16-bit PCM sample. */
    public static short ulawToPcm(byte ulawByte) {
        int u = (~ulawByte) & 0xFF;
        int t = ((u & QUANT_MASK) << 3) + BIAS;
        t <<= (u & SEG_MASK) >> SEG_SHIFT;
        return (short) (((u & SIGN_BIT) != 0) ? (BIAS - t) : (t - BIAS));
    }

    /** Decode a single A-law byte to a 16-bit PCM sample. */
    public static short alawToPcm(byte alawByte) {
        int a = (alawByte ^ 0x55) & 0xFF;
        int t = (a & QUANT_MASK) << 4;
        int seg = (a & SEG_MASK) >> SEG_SHIFT;
        switch (seg) {
            case 0 -> t += 8;
            case 1 -> t += 0x108;
            default -> {
                t += 0x108;
                t <<= seg - 1;
            }
        }
        return (short) (((a & SIGN_BIT) != 0) ? t : -t);
    }

    /**
     * Decode {@code len} µ-law bytes into {@code out}. {@code out} must have
     * capacity for at least {@code len} samples.
     */
    public static void ulawToPcm(byte[] in, int len, short[] out) {
        for (int i = 0; i < len; i++) {
            out[i] = ulawToPcm(in[i]);
        }
    }

    /** Decode {@code len} A-law bytes into {@code out}. */
    public static void alawToPcm(byte[] in, int len, short[] out) {
        for (int i = 0; i < len; i++) {
            out[i] = alawToPcm(in[i]);
        }
    }

    /** Encode a single 16-bit PCM sample to µ-law. */
    public static byte pcmToUlaw(short pcm) {
        int val = pcm >> 2;
        int mask;
        if (val < 0) {
            val = -val;
            mask = 0x7F;
        } else {
            mask = 0xFF;
        }
        if (val > CLIP) {
            val = CLIP;
        }
        val += (BIAS >> 2);
        int seg = search(val, SEG_UEND);
        if (seg >= 8) {
            return (byte) (0x7F ^ mask);
        }
        int uval = (seg << 4) | ((val >> (seg + 1)) & 0x0F);
        return (byte) (uval ^ mask);
    }

    /** Encode a single 16-bit PCM sample to A-law. */
    public static byte pcmToAlaw(short pcm) {
        int val = pcm >> 3;
        int mask;
        if (val >= 0) {
            mask = 0xD5;
        } else {
            mask = 0x55;
            val = -val - 1;
        }
        int seg = search(val, SEG_AEND);
        if (seg >= 8) {
            return (byte) (0x7F ^ mask);
        }
        int aval = seg << SEG_SHIFT;
        aval |= (seg < 2) ? ((val >> 1) & QUANT_MASK) : ((val >> seg) & QUANT_MASK);
        return (byte) (aval ^ mask);
    }

    /** Encode {@code len} PCM samples from {@code in} to µ-law bytes in {@code out}. */
    public static void pcmToUlaw(short[] in, int len, byte[] out) {
        for (int i = 0; i < len; i++) {
            out[i] = pcmToUlaw(in[i]);
        }
    }

    /** Encode {@code len} PCM samples from {@code in} to A-law bytes in {@code out}. */
    public static void pcmToAlaw(short[] in, int len, byte[] out) {
        for (int i = 0; i < len; i++) {
            out[i] = pcmToAlaw(in[i]);
        }
    }

    private static int search(int val, int[] table) {
        for (int i = 0; i < table.length; i++) {
            if (val <= table[i]) {
                return i;
            }
        }
        return table.length;
    }
}
