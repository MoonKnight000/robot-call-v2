package uz.murodjon.uysotvoice.agent.stt;

/**
 * Yandex SpeechKit STT v3 streaming (gRPC, bidirectional). Real-time recognition
 * with server-side endpointing (EOU) — no client-side utterance buffering. v3
 * supports Uzbek ({@code uz-UZ}) as well as {@code ru-RU}.
 *
 * @param apiKey         SpeechKit API key (gRPC metadata {@code authorization: Api-Key ...})
 * @param folderId       Yandex Cloud folder id (sent as {@code x-folder-id}; optional with an API key)
 * @param host           gRPC endpoint host
 * @param port           gRPC endpoint port (443, TLS)
 * @param sampleRate     raw LINEAR16_PCM input rate (8000 telephone)
 * @param model          recognition model ({@code general} by default; blank = server default)
 * @param interimResults deliver partial (interim) hypotheses in addition to finals
 */
public record YandexSttProperties(
        String apiKey,
        String folderId,
        String host,
        int port,
        int sampleRate,
        String model,
        boolean interimResults
) {
}
