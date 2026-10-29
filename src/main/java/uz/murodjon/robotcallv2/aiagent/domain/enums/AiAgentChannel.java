package uz.murodjon.robotcallv2.aiagent.domain.enums;

/**
 * Communication channel capability for an AI Agent:
 * - {@link #VOICE}: Dedicated for SIP telephony, Asterisk/LiveKit voice calls with VAD,
 *   low-latency STT/TTS, barge-in, ambient noise, and voicemail detection.
 * - {@link #CHAT}: Dedicated for text-based streaming interactions (web widgets, Telegram,
 *   mobile apps) with markdown, rich cards, and multi-turn sessions.
 * - {@link #OMNICHANNEL}: Unified agent that can participate seamlessly in both voice phone
 *   calls and text chat sessions using shared memory and prompt logic.
 */
public enum AiAgentChannel {
    VOICE,
    CHAT,
    OMNICHANNEL
}
