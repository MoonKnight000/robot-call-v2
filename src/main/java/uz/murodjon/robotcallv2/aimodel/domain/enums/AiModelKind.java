package uz.murodjon.robotcallv2.aimodel.domain.enums;

/**
 * Which of an agent's three model fields a catalog row can fill.
 *
 * <p>The families never overlap, and that is the reason the column exists: {@code nova-3}
 * is a Deepgram recognizer, {@code gemini-3.1-flash-tts-preview} a synthesizer and
 * {@code gemini-3.8-flash} a chat model, and offering any of them where another belongs
 * produces a setting that saves cleanly and fails on the call.
 */
public enum AiModelKind {

    /** {@code ai_agent.llm_model} and {@code ai_model_config.model} — the text LLM. */
    LLM,

    /** {@code ai_agent.stt_model} — the recognition model of the agent's STT provider. */
    STT,

    /** {@code ai_agent.tts_model} — the synthesis model of the agent's TTS provider. */
    TTS
}
