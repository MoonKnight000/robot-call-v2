package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * How the agent sounds, named once for every engine: the agent's default ({@code AiAgentVoice#emotion}),
 * a stage's own mood, or the one the caller's sentiment calls for. Each TTS provider turns it into
 * what it can do with the voice that speaks ({@code agent/tts/VoiceStyleMapper}), and the LLM prompt
 * turns it into wording, so the words and the voice carry the same mood.
 */
public enum VoiceEmotion {
    NEUTRAL,
    CHEERFUL,
    FRIENDLY,
    STRICT,
    WHISPER,
    SAD
}
