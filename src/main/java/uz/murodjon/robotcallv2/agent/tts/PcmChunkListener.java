package uz.murodjon.robotcallv2.agent.tts;

/** Receives PCM as {@link TtsProvider#synthesizeStreaming} produces it, chunk by chunk. */
@FunctionalInterface
public interface PcmChunkListener {

    /** @param pcm 8 kHz mono 16-bit PCM samples — one piece of the full utterance */
    void onChunk(short[] pcm);
}
